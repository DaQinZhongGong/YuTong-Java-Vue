package com.yutong.ai.memory.service;

import com.yutong.ai.memory.dto.SaveMemoryRequest;
import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AiMemoryServiceTest {

    @Test
    void save_shouldRejectBlankContent() {
        AiMemoryService service = new AiMemoryService(null);
        SaveMemoryRequest req = new SaveMemoryRequest();
        req.setContent("   ");
        assertThrows(BusinessException.class, () -> service.save(req));
    }

    @Test
    void save_shouldRejectSensitiveContent() {
        AiMemoryService service = new AiMemoryService(null);
        SaveMemoryRequest req = new SaveMemoryRequest();
        req.setContent("my api_key is secret");
        assertThrows(BusinessException.class, () -> service.save(req));
    }
}
