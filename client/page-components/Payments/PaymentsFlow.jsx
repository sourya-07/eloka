import {
	Box,
	Button,
	Center,
	Flex,
	Spinner,
	Text,
	useToast,
} from "@chakra-ui/react";
import { useUser } from "contexts";
import { fetcher } from "helpers/apiHelper";
import useRefreshToken from "hooks/useRefreshToken";
import { Fragment, useCallback, useEffect, useState } from "react";
import AddRecipient from "./AddRecipient";
import AddVendor from "./AddVendor";
import RecipientList from "./RecipientList";
import SendMoney from "./SendMoney";

const INTERACTION_SERVICE_CHECK = "384";
const INTERACTION_RECIPIENT_LIST = "780";
const INTERACTION_BANK_LIST = "155";
const INTERACTION_DELETE_RECIPIENT = "808";
const SERVICE_CODE_DMT = "45";
const RESPONSE_TYPE_ALREADY_ACTIVATED = 1295;

const STEP_LABELS = ["Service", "Vendor", "Recipient", "Transfer"];

const StepBar = ({ current }) => (
	<Flex align="center" px={1} mb={7}>
		{STEP_LABELS.map((label, i) => (
			<Fragment key={label}>
				<Flex direction="column" align="center" gap={1} flexShrink={0}>
					<Box
						w={8}
						h={8}
						borderRadius="full"
						display="flex"
						alignItems="center"
						justifyContent="center"
						bg={
							i < current
								? "green.500"
								: i === current
								? "blue.600"
								: "gray.100"
						}
						border={i === current ? "2px solid" : "2px solid"}
						borderColor={
							i < current
								? "green.500"
								: i === current
								? "blue.600"
								: "gray.200"
						}
						transition="all 0.25s"
					>
						<Text
							fontSize="xs"
							fontWeight="bold"
							lineHeight="1"
							color={i <= current ? "white" : "gray.400"}
						>
							{i < current ? "✓" : i + 1}
						</Text>
					</Box>
					<Text
						fontSize="xxs"
						fontWeight={i === current ? "semibold" : "normal"}
						color={
							i < current
								? "green.600"
								: i === current
								? "blue.600"
								: "gray.400"
						}
					>
						{label}
					</Text>
				</Flex>
				{i < STEP_LABELS.length - 1 && (
					<Box
						flex={1}
						h="2px"
						bg={i < current ? "green.400" : "gray.200"}
						mx={1.5}
						mb={5}
						borderRadius="full"
						transition="background 0.3s"
					/>
				)}
			</Fragment>
		))}
	</Flex>
);

/**
 * PaymentsFlow — full DMT flow entry point.
 *
 * Step 1: Service activation check (384 / service_code 45)
 * Step 2: AddVendor — mobile lookup (150) + optional add (110)
 * Step 3: Load recipient list (780) + bank list (155) using vendor's customer_id
 * Step 4: Route to RecipientList or AddRecipient
 * Step 5: SendMoney
 */
const PaymentsFlow = () => {
	const { userData, accessToken } = useUser();
	const userDetails = userData?.userDetails ?? {};
	const { generateNewToken } = useRefreshToken();
	const toast = useToast();

	const [view, setView] = useState("activating");
	const [customerId, setCustomerId] = useState(null);
	const [recipients, setRecipients] = useState([]);
	const [bankList, setBankList] = useState([]);
	const [selectedRecipient, setSelectedRecipient] = useState(null);
	const [loadingLabel, setLoadingLabel] = useState("Checking service...");
	const [deletingRecipientId, setDeletingRecipientId] = useState(null);

	const currentStep = (() => {
		if (view === "activating" || view === "activation-error") return 0;
		if (view === "add-vendor") return 1;
		if (view === "loading") return customerId ? 2 : 1;
		if (view === "add-recipient" || view === "recipient-list") return 2;
		if (view === "send-money") return 3;
		return 0;
	})();

	// ── Payload builders ────────────────────────────────────────────────────────

	const buildServicePayload = useCallback(
		() => ({
			interaction_type_id: INTERACTION_SERVICE_CHECK,
			bc: userDetails?.org_id || "1",
			latlong: "",
			source: "NEWCONNECT",
			locale: "en",
			version: "v2",
			user_code: userDetails?.code || "",
			initiator_id: userDetails?.mobile || "",
			org_id: userDetails?.org_id || "1",
			service_code: SERVICE_CODE_DMT,
			lang: "null",
			realsourceip: "",
		}),
		[userDetails]
	);

	const buildRecipientPayload = useCallback(
		(vendorMobile) => ({
			interaction_type_id: INTERACTION_RECIPIENT_LIST,
			bc: userDetails?.org_id || "1",
			latlong: "",
			sender_name: userDetails?.name || "",
			source: "NEWCONNECT",
			locale: "en",
			version: "v2",
			user_code: userDetails?.code || "",
			initiator_id: userDetails?.mobile || "",
			org_id: userDetails?.org_id || "1",
			service_code: SERVICE_CODE_DMT,
			customer_id: vendorMobile,
			lang: "null",
			realsourceip: "",
		}),
		[userDetails]
	);

	const buildBankPayload = useCallback(
		() => ({
			interaction_type_id: INTERACTION_BANK_LIST,
			bc: userDetails?.org_id || "1",
			sender_name: userDetails?.name || "",
			source: "NEWCONNECT",
			locale: "en",
			version: "v2",
			ifsc_required: "1",
			user_code: userDetails?.code || "",
			initiator_id: userDetails?.mobile || "",
			bank_id: "",
			org_id: userDetails?.org_id || "1",
			customer_id: userDetails?.mobile || "",
			lang: "null",
			ifsc_bankcode: "",
			varifiable: "1",
			realsourceip: "",
		}),
		[userDetails]
	);

	const buildDeleteRecipientPayload = useCallback(
		(recipient) => ({
			interaction_type_id: INTERACTION_DELETE_RECIPIENT,
			bc: userDetails?.org_id || "1",
			source: "NEWCONNECT",
			locale: "en",
			version: "v2",
			client_ref_id: Date.now().toString(),
			recipient_id_type: recipient?.recipient_id_type || "mobile_number",
			recipient_type: "2",
			user_code: userDetails?.code || "",
			next_allowed_limit: "",
			initiator_id: userDetails?.mobile || "",
			org_id: userDetails?.org_id || "1",
			customer_id: customerId || "",
			lang: "null",
			recipient_id: String(recipient?.recipient_id || ""),
			realsourceip: "",
		}),
		[userDetails, customerId]
	);

	// ── Service activation ───────────────────────────────────────────────────────

	const checkAndActivateService = useCallback(async () => {
		setLoadingLabel("Checking service...");
		setView("activating");
		try {
			const checkData = await fetcher(
				"/api/transaction",
				{ token: accessToken, body: buildServicePayload(), timeout: 30000 },
				generateNewToken
			);

			const isActivated =
				checkData?.response_type_id === RESPONSE_TYPE_ALREADY_ACTIVATED ||
				checkData?.data?.service_status === "1";

			if (!isActivated) {
				setLoadingLabel("Activating service...");
				const activateData = await fetcher(
					"/api/transaction",
					{ token: accessToken, body: buildServicePayload(), timeout: 30000 },
					generateNewToken
				);
				const activated =
					activateData?.service_status === "1" ||
					activateData?.data?.service_status === "1";

				if (!activated) {
					toast({
						title:
							activateData?.message || "Service activation failed.",
						status: "error",
						duration: 5000,
					});
					setView("activation-error");
					return;
				}
			}

			setView("add-vendor");
		} catch (err) {
			console.error("[PaymentsFlow] service check error:", err);
			toast({
				title: "Service check failed. Please try again.",
				status: "error",
				duration: 5000,
			});
			setView("activation-error");
		}
	}, [accessToken, buildServicePayload, generateNewToken, toast]);

	useEffect(() => {
		checkAndActivateService();
	}, []); // eslint-disable-line react-hooks/exhaustive-deps

	// ── Post-vendor data loading ─────────────────────────────────────────────────

	const loadRecipientData = useCallback(
		async (vendorMobile, skipRecipients) => {
			setLoadingLabel("Loading recipients...");
			setView("loading");
			try {
				if (skipRecipients) {
					const bankData = await fetcher(
						"/api/transaction",
						{ token: accessToken, body: buildBankPayload(), timeout: 30000 },
						generateNewToken
					);
					setBankList(bankData?.param_attributes?.list_elements || []);
					setView("add-recipient");
				} else {
					const [recipientData, bankData] = await Promise.all([
						fetcher(
							"/api/transaction",
							{
								token: accessToken,
								body: buildRecipientPayload(vendorMobile),
								timeout: 30000,
							},
							generateNewToken
						),
						fetcher(
							"/api/transaction",
							{
								token: accessToken,
								body: buildBankPayload(),
								timeout: 30000,
							},
							generateNewToken
						),
					]);

					const fundList = recipientData?.data?.fund_transfer_list || [];
					setRecipients(fundList);
					setBankList(bankData?.param_attributes?.list_elements || []);
					setView(fundList.length > 0 ? "recipient-list" : "add-recipient");
				}
			} catch (err) {
				console.error("[PaymentsFlow] load error:", err);
				setView("add-recipient");
			}
		},
		[accessToken, buildBankPayload, buildRecipientPayload, generateNewToken]
	);

	// ── Handlers ────────────────────────────────────────────────────────────────

	const handleVendorReady = useCallback(
		(vendorMobile, isNewVendor) => {
			setCustomerId(vendorMobile);
			loadRecipientData(vendorMobile, isNewVendor);
		},
		[loadRecipientData]
	);

	const handleRecipientAdded = useCallback(() => {
		if (!customerId) return;
		loadRecipientData(customerId, false);
	}, [customerId, loadRecipientData]);

	const handleSelectRecipient = useCallback((recipient) => {
		setSelectedRecipient(recipient);
		setView("send-money");
	}, []);

	const handleDeleteRecipient = useCallback(
		async (recipient) => {
			setDeletingRecipientId(recipient.recipient_id);
			const payload = buildDeleteRecipientPayload(recipient);
			console.log("[DELETE RECIPIENT] payload", payload);
			try {
				const res = await fetcher(
					"/api/transaction",
					{ token: accessToken, body: payload, timeout: 30000 },
					generateNewToken
				);
				console.log("[DELETE RECIPIENT] response", res);

				if (res?.status === 0 || res?.message === "DELETED!") {
					setRecipients((prev) => {
						const updated = prev.filter(
							(r) => r.recipient_id !== recipient.recipient_id
						);
						if (updated.length === 0) {
							setView("add-recipient");
						}
						return updated;
					});
					toast({
						title: "Recipient deleted successfully",
						status: "success",
						duration: 3000,
					});
				} else {
					toast({
						title: res?.message || "Failed to delete recipient",
						status: "error",
						duration: 5000,
					});
				}
			} catch (err) {
				console.error("[DELETE RECIPIENT]", err);
				toast({
					title: "Failed to delete recipient. Please try again.",
					status: "error",
					duration: 5000,
				});
			} finally {
				setDeletingRecipientId(null);
			}
		},
		[
			accessToken,
			buildDeleteRecipientPayload,
			generateNewToken,
			toast,
		]
	);

	// ── Loading ──────────────────────────────────────────────────────────────────

	if (view === "activating" || view === "loading") {
		return (
			<Center minH="55vh">
				<Flex direction="column" align="center" gap={5}>
					<Spinner
						size="xl"
						color="blue.500"
						thickness="3px"
						speed="0.75s"
						emptyColor="blue.50"
					/>
					<Text fontSize="sm" color="gray.400" fontWeight="medium" letterSpacing="wide">
						{loadingLabel}
					</Text>
				</Flex>
			</Center>
		);
	}

	// ── Error ────────────────────────────────────────────────────────────────────

	if (view === "activation-error") {
		return (
			<Center minH="55vh" px={4}>
				<Box
					maxW="360px"
					w="full"
					p={8}
					textAlign="center"
					bg="white"
					borderRadius="2xl"
					boxShadow="basic"
					border="1px solid"
					borderColor="red.100"
				>
					<Box
						w={14}
						h={14}
						borderRadius="full"
						bg="red.50"
						border="2px solid"
						borderColor="red.200"
						display="flex"
						alignItems="center"
						justifyContent="center"
						mx="auto"
						mb={5}
					>
						<Text
							fontSize="2xl"
							fontWeight="black"
							color="red.400"
							lineHeight="1"
						>
							!
						</Text>
					</Box>
					<Text
						fontWeight="bold"
						fontSize="lg"
						color="gray.800"
						mb={2}
					>
						Activation Failed
					</Text>
					<Text fontSize="sm" color="gray.500" mb={7} lineHeight="tall">
						Could not activate the Money Transfer service. Please check your
						connection and try again.
					</Text>
					<Button
						colorScheme="blue"
						w="full"
						borderRadius="full"
						h={11}
						fontWeight="semibold"
						onClick={checkAndActivateService}
					>
						Try Again
					</Button>
				</Box>
			</Center>
		);
	}

	// ── Content views ────────────────────────────────────────────────────────────

	return (
		<Box maxW="560px" mx="auto" pt={6} px={{ base: 4, md: 0 }} pb={12}>
			<StepBar current={currentStep} />

			{view === "add-vendor" && (
				<AddVendor onVendorReady={handleVendorReady} />
			)}

			{view === "add-recipient" && (
				<AddRecipient
					customerId={customerId}
					bankList={bankList}
					onSuccess={handleRecipientAdded}
					onBack={
						recipients.length > 0
							? () => setView("recipient-list")
							: null
					}
				/>
			)}

			{view === "recipient-list" && (
				<RecipientList
					recipients={recipients}
					onSelectRecipient={handleSelectRecipient}
					onAddRecipient={() => setView("add-recipient")}
					onDeleteRecipient={handleDeleteRecipient}
					deletingRecipientId={deletingRecipientId}
				/>
			)}

			{view === "send-money" && (
				<SendMoney
					customerId={customerId}
					recipient={selectedRecipient}
					onBack={() => setView("recipient-list")}
					onSuccess={() => setView("recipient-list")}
				/>
			)}
		</Box>
	);
};

export default PaymentsFlow;
