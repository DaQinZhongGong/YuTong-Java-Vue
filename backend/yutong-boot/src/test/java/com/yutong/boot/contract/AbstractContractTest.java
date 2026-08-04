package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.boot.YutongApplication;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GA2-L180 契约测试基类。
 * 设计来源: GA2-L180 CT-&lt;operationId&gt; 契约测试基础设施任务
 *
 * 提供:
 * <ul>
 *   <li>Spring Boot 集成测试上下文 (test profile)</li>
 *   <li>MockMvc HTTP 调用辅助 (performPost/Get/Put/Delete)</li>
 *   <li>Mock 用户头注入 (mockUser: admin/biz/approver/viewer)</li>
 *   <li>统一响应解析与断言 (parseResult/assertSuccess/assertError/assertTraceIdPresent)</li>
 * </ul>
 *
 * 子类继承后只需编写 @Test 方法, 通过 performXxx 调用 REST 端点并断言 Result。
 */
@SpringBootTest(classes = YutongApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractContractTest {

    /** Mock 用户请求头名 (与 MockAuthAdapter.MOCK_USER_HEADER 一致)。 */
    public static final String MOCK_USER_HEADER = "X-Mock-User";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    // ===== Mock 用户头辅助 =====

    /**
     * 构造携带 X-Mock-User 头的 HttpHeaders。
     *
     * @param type Mock 用户类型: admin/biz/approver/viewer
     * @return 含 X-Mock-User 与 Accept 头的 HttpHeaders
     */
    protected HttpHeaders mockUser(String type) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(MOCK_USER_HEADER, type);
        return headers;
    }

    // ===== HTTP 调用辅助 =====

    /**
     * 执行 POST 请求。
     *
     * @param path    请求路径
     * @param body    请求体对象 (null 表示无 body)
     * @param headers 请求头 (null 表示无自定义头)
     * @return MvcResult
     */
    protected MvcResult performPost(String path, Object body, HttpHeaders headers) throws Exception {
        byte[] content = body != null ? objectMapper.writeValueAsBytes(body) : new byte[0];
        return mockMvc.perform(MockMvcRequestBuilders.post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .headers(headers != null ? headers : new HttpHeaders())
                        .content(content))
                .andReturn();
    }

    /**
     * 执行 GET 请求。
     *
     * @param path    请求路径
     * @param headers 请求头 (null 表示无自定义头)
     * @return MvcResult
     */
    protected MvcResult performGet(String path, HttpHeaders headers) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(path)
                        .accept(MediaType.APPLICATION_JSON)
                        .headers(headers != null ? headers : new HttpHeaders()))
                .andReturn();
    }

    /**
     * 执行 PUT 请求。
     *
     * @param path    请求路径
     * @param body    请求体对象 (null 表示无 body)
     * @param headers 请求头 (null 表示无自定义头)
     * @return MvcResult
     */
    protected MvcResult performPut(String path, Object body, HttpHeaders headers) throws Exception {
        byte[] content = body != null ? objectMapper.writeValueAsBytes(body) : new byte[0];
        return mockMvc.perform(MockMvcRequestBuilders.put(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .headers(headers != null ? headers : new HttpHeaders())
                        .content(content))
                .andReturn();
    }

    /**
     * 执行 DELETE 请求。
     *
     * @param path    请求路径
     * @param headers 请求头 (null 表示无自定义头)
     * @return MvcResult
     */
    protected MvcResult performDelete(String path, HttpHeaders headers) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.delete(path)
                        .accept(MediaType.APPLICATION_JSON)
                        .headers(headers != null ? headers : new HttpHeaders()))
                .andReturn();
    }

    /**
     * 执行 multipart/form-data 文件上传请求 (POST)。
     *
     * <p>用于 File 域 uploadFile 等 multipart 端点测试。封装 MockMultipartFile 构造与 multipart 构建器。
     *
     * @param path             请求路径
     * @param fileContent      文件二进制内容
     * @param originalFilename 原始文件名 (含扩展名, 用于扩展名白名单校验)
     * @param contentType      Content-Type (MIME, 如 text/plain)
     * @param headers          请求头 (null 表示无自定义头)
     * @return MvcResult
     */
    protected MvcResult performFileUpload(String path, byte[] fileContent, String originalFilename,
                                          String contentType, HttpHeaders headers) throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile("file", originalFilename, contentType, fileContent);
        return mockMvc.perform(MockMvcRequestBuilders.multipart(path)
                        .file(mockFile)
                        .accept(MediaType.APPLICATION_JSON)
                        .headers(headers != null ? headers : new HttpHeaders()))
                .andReturn();
    }

    // ===== 响应解析与断言 =====

    /**
     * 解析 MvcResult 响应为 ResultNode (含 code/message/data/traceId)。
     * data 以 JsonNode 形式返回 (JSON null/缺失时为 null)。
     */
    protected ResultNode parseResult(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(content);
        String code = textOrNull(root, "code");
        String message = textOrNull(root, "message");
        String traceId = textOrNull(root, "traceId");
        JsonNode dataNode = root.get("data");
        JsonNode data = (dataNode == null || dataNode.isNull()) ? null : dataNode;
        return new ResultNode(code, message, data, traceId);
    }

    /**
     * 断言响应为成功 (code=0)。
     */
    protected void assertSuccess(MvcResult result) throws Exception {
        ResultNode node = parseResult(result);
        assertEquals("0", node.code(),
                "expected success (code=0) but got code=" + node.code()
                        + " message=" + node.message() + " traceId=" + node.traceId());
    }

    /**
     * 断言响应为指定错误码。
     */
    protected void assertError(MvcResult result, String expectedCode) throws Exception {
        ResultNode node = parseResult(result);
        assertEquals(expectedCode, node.code(),
                "expected error code=" + expectedCode + " but got code=" + node.code()
                        + " message=" + node.message() + " traceId=" + node.traceId());
    }

    /**
     * 断言 traceId 非空且长度在 16-64 之间。
     */
    protected void assertTraceIdPresent(MvcResult result) throws Exception {
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "traceId should not be null");
        int len = node.traceId().length();
        assertTrue(len >= 16 && len <= 64,
                "traceId length should be 16-64 but was " + len + " traceId=" + node.traceId());
    }

    /**
     * 从 JsonNode 读取字符串字段, null/缺失时返回 null。
     */
    private static String textOrNull(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        return (node == null || node.isNull()) ? null : node.asText();
    }

    /**
     * 解析后的统一响应节点 (轻量版, 仅契约测试断言所需字段)。
     */
    public record ResultNode(String code, String message, JsonNode data, String traceId) {
        public boolean success() {
            return "0".equals(code);
        }
    }
}
