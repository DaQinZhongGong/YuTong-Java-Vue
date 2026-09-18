package com.yutong.ai.aiflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.aiflow.service.AiflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiflowEngine 条件算子测试。
 * 覆盖: == != > < >= <= contains startsWith endsWith empty notEmpty in
 */
class AiflowEngineConditionTest {

    private AiflowEngine engine;
    private Method evaluateCondition;
    private Map<String, Object> inputCtx;
    private Map<String, Object> globalOutputs;

    @BeforeEach
    void setUp() throws Exception {
        // 构造最小 AiflowEngine (7 参, 依赖均为 null 不影响条件求值)
        engine = new AiflowEngine(new ObjectMapper(), null, null, null, null, null, null);
        evaluateCondition = AiflowEngine.class.getDeclaredMethod("evaluateCondition",
                String.class, Map.class, Map.class);
        evaluateCondition.setAccessible(true);
        inputCtx = new HashMap<>();
        globalOutputs = new HashMap<>();
    }

    private boolean eval(String cond) throws Exception {
        return (boolean) evaluateCondition.invoke(engine, cond, inputCtx, globalOutputs);
    }

    @Test
    void equals_operator() throws Exception {
        inputCtx.put("status", "SUCCESS");
        assertTrue(eval("status==SUCCESS"));
        assertTrue(eval("status=='SUCCESS'"));
        assertFalse(eval("status==FAILED"));
    }

    @Test
    void notEquals_operator() throws Exception {
        inputCtx.put("status", "SUCCESS");
        assertTrue(eval("status!=FAILED"));
        assertFalse(eval("status!=SUCCESS"));
    }

    @Test
    void greaterThan_operator() throws Exception {
        inputCtx.put("count", "10");
        assertTrue(eval("count>5"));
        assertFalse(eval("count>10"));
        assertFalse(eval("count>15"));
    }

    @Test
    void lessThan_operator() throws Exception {
        inputCtx.put("count", "10");
        assertTrue(eval("count<15"));
        assertFalse(eval("count<10"));
        assertFalse(eval("count<5"));
    }

    @Test
    void greaterOrEqual_operator() throws Exception {
        inputCtx.put("score", "80");
        assertTrue(eval("score>=80"));
        assertTrue(eval("score>=70"));
        assertFalse(eval("score>=90"));
    }

    @Test
    void lessOrEqual_operator() throws Exception {
        inputCtx.put("score", "80");
        assertTrue(eval("score<=80"));
        assertTrue(eval("score<=90"));
        assertFalse(eval("score<=70"));
    }

    @Test
    void contains_operator() throws Exception {
        inputCtx.put("text", "Hello World YuTong");
        assertTrue(eval("contains text World"));
        assertTrue(eval("contains text 'YuTong'"));
        assertFalse(eval("contains text Foo"));
    }

    @Test
    void startsWith_operator() throws Exception {
        inputCtx.put("name", "YuTong-Platform");
        assertTrue(eval("startsWith name YuTong"));
        assertFalse(eval("startsWith name Platform"));
    }

    @Test
    void endsWith_operator() throws Exception {
        inputCtx.put("filename", "report.pdf");
        assertTrue(eval("endsWith filename .pdf"));
        assertFalse(eval("endsWith filename .doc"));
    }

    @Test
    void empty_operator() throws Exception {
        inputCtx.put("field1", "");
        inputCtx.put("field2", "value");
        assertTrue(eval("empty field1"));
        assertFalse(eval("empty field2"));
    }

    @Test
    void notEmpty_operator() throws Exception {
        inputCtx.put("field1", "");
        inputCtx.put("field2", "value");
        assertFalse(eval("notEmpty field1"));
        assertTrue(eval("notEmpty field2"));
    }

    @Test
    void in_operator() throws Exception {
        inputCtx.put("type", "image");
        assertTrue(eval("type in image,video,audio"));
        assertTrue(eval("type in 'image,video,audio'"));
        assertFalse(eval("type in text,document"));
    }

    @Test
    void numericParseFailure_failsClosed() throws Exception {
        inputCtx.put("field", "not_a_number");
        assertFalse(eval("field>5"));
        assertFalse(eval("field<10"));
    }

    @Test
    void nullField_failsClosed() throws Exception {
        assertFalse(eval("nonexistent>5"));
        assertFalse(eval("contains nonexistent foo"));
    }

    @Test
    void blankCondition_returnsTrue() throws Exception {
        assertTrue(eval(""));
        assertTrue(eval(null));
    }
}
