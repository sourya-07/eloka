package in.eko.eloka.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/system")
class SystemInfoController {

	private final SystemInfoService systemInfoService;

	SystemInfoController(SystemInfoService systemInfoService) {
		this.systemInfoService = systemInfoService;
	}

	@GetMapping("/info")
	SystemInfo info() {
		return systemInfoService.current();
	}
}
