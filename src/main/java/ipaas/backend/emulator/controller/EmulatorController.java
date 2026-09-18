package ipaas.backend.emulator.controller;

import ipaas.backend.emulator.service.EmulatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class EmulatorController {

    private final EmulatorService emulatorService;

    public EmulatorController(EmulatorService emulatorService) {
        this.emulatorService = emulatorService;
    }

    @RequestMapping("/backend/emulator/**")
    public ResponseEntity<StreamingResponseBody> handle(HttpServletRequest request,
                                                        HttpServletResponse response) throws IOException {
        response.setBufferSize(0);

        Map<String, String> headers = new LinkedHashMap<String, String>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name, request.getHeader(name));
        }

        String body = new String(readBytes(request.getInputStream()), StandardCharsets.UTF_8);

        return emulatorService.handle(request.getMethod(), request.getRequestURI(), headers, body);
    }

    private byte[] readBytes(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) {
            bos.write(buffer, 0, read);
        }
        return bos.toByteArray();
    }
}
