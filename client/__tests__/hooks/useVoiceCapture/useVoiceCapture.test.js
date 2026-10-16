import { useVoiceCapture } from "hooks";
import { act, renderHook } from "test-utils";

// Use fake timers for testing time-dependent functionality
jest.useFakeTimers();

// ---------------------------------------------------------------------------
//  Mock browser audio APIs that aren't available in Node.js testing environment
// ---------------------------------------------------------------------------

// Mock MediaStream
class MockMediaStream {
	constructor() {
		this.tracks = [];
	}
	getTracks() {
		return this.tracks;
	}
	addTrack(track) {
		this.tracks.push(track);
	}
}
global.MediaStream = MockMediaStream;

// Mock AudioContext with audioWorklet
class MockAudioContext {
	constructor() {
		this.state = "running";
		this.sampleRate = 16000;
		this.destination = { channelCount: 2 };
		this.audioWorklet = {
			addModule: jest.fn().mockResolvedValue(undefined),
		};
	}

	createMediaStreamSource() {
		return { connect: jest.fn() };
	}

	close() {
		this.state = "closed";
		return Promise.resolve();
	}
}
global.AudioContext = MockAudioContext;

// Mock AudioWorkletNode (VAD); keep the latest node so tests can post VAD messages
let lastVadNode = null;
class MockAudioWorkletNode {
	constructor() {
		this.port = { onmessage: null, postMessage: jest.fn() };
		lastVadNode = this;
	}
}
global.AudioWorkletNode = MockAudioWorkletNode;

// Mock getUserMedia
Object.defineProperty(navigator, "mediaDevices", {
	value: {
		getUserMedia: jest.fn().mockResolvedValue(new MockMediaStream()),
	},
	configurable: true,
});

// URL.createObjectURL is not implemented in jsdom
global.URL.createObjectURL = jest.fn(() => "blob:mock");

// ---------------------------------------------------------------------------
//  Mock the native MediaRecorder with controllable callbacks
// ---------------------------------------------------------------------------
const startRecording = jest.fn();
const stopRecording = jest.fn();
let lastRecorder = null;
class MockMediaRecorder {
	constructor(stream) {
		this.stream = stream;
		this.state = "inactive";
		this.ondataavailable = null;
		this.onstop = null;
		lastRecorder = this;
	}
	start() {
		this.state = "recording";
		startRecording();
	}
	stop() {
		this.state = "inactive";
		stopRecording();
		this.onstop?.();
	}
}
global.MediaRecorder = MockMediaRecorder;

describe("useVoiceCapture hook", () => {
	beforeAll(() => {
		jest.useFakeTimers();
	});
	beforeEach(() => {
		jest.clearAllMocks();
		startRecording.mockClear();
		stopRecording.mockClear();
		lastRecorder = null;
		lastVadNode = null;
		jest.clearAllTimers();
	});
	afterAll(() => {
		jest.useRealTimers();
	});

	it("initializes with idle status", () => {
		const { result } = renderHook(() => useVoiceCapture());
		expect(result.current.status).toBe("idle");
		expect(typeof result.current.start).toBe("function");
		expect(typeof result.current.stop).toBe("function");
	});

	it("auto-stops recording after maxDurationMs (30s by default)", async () => {
		const { result } = renderHook(() => useVoiceCapture());

		// Start recording
		await act(async () => {
			await result.current.start();
		});

		expect(startRecording).toHaveBeenCalled();
		expect(result.current.status).toBe("recording");

		// Fast-forward time to just before timeout
		act(() => {
			jest.advanceTimersByTime(29999);
		});
		expect(stopRecording).not.toHaveBeenCalled();

		// Fast-forward to timeout
		act(() => {
			jest.advanceTimersByTime(2);
		});

		expect(stopRecording).toHaveBeenCalled();
		expect(result.current.status).toBe("idle");
	});

	it("stops when size limit is exceeded", async () => {
		const { result } = renderHook(() => useVoiceCapture());

		await act(async () => {
			await result.current.start();
		});

		// Chunks are only collected after speech is detected by the VAD
		act(() => {
			lastVadNode.port.onmessage({ data: { cmd: "speech" } });
		});

		// Simulate a data chunk larger than the 25 MB default limit
		// (a size-only stand-in avoids allocating a real 26 MB blob)
		const hugeChunk = { size: 26 * 1024 * 1024 };

		act(() => {
			lastRecorder.ondataavailable({ data: hugeChunk });
		});

		expect(stopRecording).toHaveBeenCalled();
		expect(result.current.error).toBeInstanceOf(Error);
	});

	/*
	it("stops after silence detection (4s of silence + 250ms debounce)", async () => {
		const { result } = renderHook(() => useVoiceCapture());

		await act(async () => {
			await result.current.start();
		});

		// Allow port.onmessage to be stored
		await act(async () => {
			await new Promise((resolve) => setTimeout(resolve, 10));
		});

		// Ensure vadOnMessageCallback was set
		expect(vadOnMessageCallback).not.toBeNull();

		// Simulate speech detection
		act(() => {
			if (vadOnMessageCallback) {
				vadOnMessageCallback({ data: { type: "speech", volume: 0.8 } });
			}
		});

		// Fast forward 1 second - still speaking
		act(() => {
			jest.advanceTimersByTime(1000);
		});

		// Switch to silence
		act(() => {
			if (vadOnMessageCallback) {
				vadOnMessageCallback({
					data: { type: "silence", volume: 0.1 },
				});
			}
		});

		// Advance time by 3.9 seconds (not enough for auto-stop)
		act(() => {
			jest.advanceTimersByTime(3900);
		});
		expect(stopRecording).not.toHaveBeenCalled();

		// Advance time past the 4s silence threshold + debounce
		act(() => {
			jest.advanceTimersByTime(350);
		});

		expect(stopRecording).toHaveBeenCalled();
	});

	it("responds to push-to-talk key (space by default)", async () => {
		const { result } = renderHook(() => useVoiceCapture());

		// Small delay to ensure hook is fully set up
		await act(async () => {
			await new Promise((resolve) => setTimeout(resolve, 10));
		});

		// Simulate space key down
		act(() => {
			window.dispatchEvent(new KeyboardEvent("keydown", { key: " " }));
		});
		expect(startRecording).toHaveBeenCalled();

		// Simulate space key up
		act(() => {
			window.dispatchEvent(new KeyboardEvent("keyup", { key: " " }));
		});
		expect(stopRecording).toHaveBeenCalled();
	});

	it("accepts custom options", async () => {
		// Test with custom options
		const onStop = jest.fn();
		const onDataChunk = jest.fn();

		const { result } = renderHook(() =>
			useVoiceCapture({
				onStop,
				onDataChunk,
				vadAggressiveness: 3,
				pushToTalkKey: "Enter",
				maxDurationMs: 10000,
				maxSizeBytes: 10 * 1024 * 1024,
			})
		);

		await act(async () => {
			await result.current.start();
		});

		// Test custom timeout
		act(() => {
			jest.advanceTimersByTime(9999);
		});
		expect(stopRecording).not.toHaveBeenCalled();

		act(() => {
			jest.advanceTimersByTime(1);
		});
		expect(stopRecording).toHaveBeenCalled();

		// Manually trigger stop callback to test onStop
		act(() => {
			if (stopCallback) {
				const mockBlobUrl = "blob:mock";
				const mockBlob = new Blob([], { type: "audio/webm" });
				stopCallback(mockBlobUrl, mockBlob);
			}
		});

		expect(onStop).toHaveBeenCalled();

		// Test custom push-to-talk key
		startRecording.mockClear();
		stopRecording.mockClear();

		act(() => {
			window.dispatchEvent(
				new KeyboardEvent("keydown", { key: "Enter" })
			);
		});
		expect(startRecording).toHaveBeenCalled();

		act(() => {
			window.dispatchEvent(new KeyboardEvent("keyup", { key: "Enter" }));
		});
		expect(stopRecording).toHaveBeenCalled();
	});

	it("handles onDataChunk callback", async () => {
		const onDataChunk = jest.fn();
		renderHook(() => useVoiceCapture({ onDataChunk }));

		await act(async () => {
			// Allow hook to initialize
			await new Promise((resolve) => setTimeout(resolve, 10));
		});

		// Simulate data available
		const mockBlob = new Blob(["test"], { type: "audio/webm" });

		act(() => {
			if (dataAvailableCallback) {
				dataAvailableCallback(mockBlob);
			}
		});

		expect(onDataChunk).toHaveBeenCalledWith(mockBlob);
	});

	it("updates volume state on VAD events", async () => {
		const { result } = renderHook(() => useVoiceCapture());

		await act(async () => {
			await result.current.start();
		});

		// Allow port.onmessage to be stored
		await act(async () => {
			await new Promise((resolve) => setTimeout(resolve, 10));
		});

		// Simulate VAD event with volume
		act(() => {
			if (vadOnMessageCallback) {
				vadOnMessageCallback({
					data: { type: "speech", volume: 0.75 },
				});
			}
		});

		expect(result.current.volume).toBe(0.75);
	});
    */
});
