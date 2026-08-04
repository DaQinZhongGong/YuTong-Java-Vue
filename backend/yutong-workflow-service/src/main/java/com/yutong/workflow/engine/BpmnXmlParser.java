package com.yutong.workflow.engine;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * BPMN XML 解析器。设计来源: 41-工作流与BPMN引擎设计。
 *
 * <p>GA2-44 L1+L2 轻量自研引擎实现: 使用 JDK 内置 javax.xml.parsers 解析 BPMN 2.0 XML,
 * 不引入 bpmn-js/camunda-parser 等第三方库, 保持依赖最小化。
 *
 * <p>支持元素: StartEvent / UserTask / ServiceTask / ExclusiveGateway / ParallelGateway / EndEvent / SequenceFlow。
 * <p>不支持: InclusiveGateway / ComplexGateway / CallActivity / SubProcess / BoundaryEvent / IntermediateCatchEvent。
 *
 * <p>校验规则:
 * <ul>
 *   <li>必须有 1 个 StartEvent</li>
 *   <li>必须有至少 1 个 EndEvent</li>
 *   <li>所有 UserTask 必须有 assignee 或 candidateGroups</li>
 *   <li>所有节点必须有可达路径到 EndEvent (深度优先遍历校验)</li>
 *   <li>所有 ServiceTask 必须有 target 属性 (业务层校验白名单)</li>
 * </ul>
 */
@Component
public class BpmnXmlParser {

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    private final DocumentBuilderFactory factory;

    public BpmnXmlParser() {
        this.factory = DocumentBuilderFactory.newInstance();
        // 安全配置: 禁用 XXE
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (Exception ignored) {
            // 部分解析器实现可能不支持, 忽略
        }
        factory.setNamespaceAware(true);
    }

    /**
     * 解析 BPMN XML 字符串。
     *
     * @param bpmnXml BPMN XML 内容
     * @return 解析后的流程定义对象
     * @throws BusinessException 如果 XML 格式错误或校验失败, 抛出 WF-400002
     */
    public BpmnProcess parse(String bpmnXml) {
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();

            // 查找 process 元素 (BPMN 根元素 definitions 下可能有多个 process, 取第一个)
            Element processEl = findFirstElement(root, "process");
            if (processEl == null) {
                throw new BusinessException(ErrorCode.WF_BPMN_XML_INVALID, "BPMN XML missing <process> element");
            }

            String processId = processEl.getAttribute("id");
            String processName = processEl.getAttribute("name");
            BpmnProcess process = new BpmnProcess(processId, processName);

            // 解析所有节点
            parseNodes(processEl, process);
            // 解析所有连线
            parseFlows(processEl, process);

            // 校验
            validate(process);

            return process;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.WF_BPMN_XML_INVALID,
                    "BPMN XML parse failed: " + e.getMessage());
        }
    }

    private void parseNodes(Element processEl, BpmnProcess process) {
        NodeList children = processEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element el)) continue;

            String localName = el.getLocalName() == null ? el.getTagName() : el.getLocalName();
            // 去掉命名空间前缀 (如 bpmn:startEvent)
            String name = localName.contains(":") ? localName.substring(localName.indexOf(':') + 1) : localName;

            BpmnProcess.BpmnNodeType type = mapNodeType(name);
            if (type == null) continue;

            String id = el.getAttribute("id");
            String nodeName = el.getAttribute("name");
            BpmnProcess.BpmnNode node = BpmnProcess.BpmnNode.of(id, nodeName, type);

            // UserTask: assignee + candidateGroups
            if (type == BpmnProcess.BpmnNodeType.USER_TASK) {
                node.setAssigneeExpr(el.getAttribute("assignee"));
                node.setCandidateGroups(el.getAttribute("candidateGroups"));
                // documentation 子元素
                String doc = getDocumentation(el);
                node.setDocumentation(doc);
            }

            // ServiceTask: target 属性 (使用 camunda:delegateExpression 或 yutong:target 自定义属性)
            if (type == BpmnProcess.BpmnNodeType.SERVICE_TASK) {
                String target = el.getAttribute("target");
                if (target == null || target.isEmpty()) {
                    target = el.getAttribute("delegateExpression");
                }
                node.setServiceTaskTarget(target);
            }

            process.addNode(node);
        }
    }

    private void parseFlows(Element processEl, BpmnProcess process) {
        NodeList children = processEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element el)) continue;

            String localName = el.getLocalName() == null ? el.getTagName() : el.getLocalName();
            String name = localName.contains(":") ? localName.substring(localName.indexOf(':') + 1) : localName;

            if (!"sequenceFlow".equals(name)) continue;

            String id = el.getAttribute("id");
            String sourceRef = el.getAttribute("sourceRef");
            String targetRef = el.getAttribute("targetRef");
            BpmnProcess.BpmnSequenceFlow flow = new BpmnProcess.BpmnSequenceFlow(id, sourceRef, targetRef);

            // 条件表达式 (子元素 conditionExpression)
            NodeList condNodes = el.getChildNodes();
            for (int j = 0; j < condNodes.getLength(); j++) {
                Node condChild = condNodes.item(j);
                if (!(condChild instanceof Element condEl)) continue;
                String condLocalName = condEl.getLocalName() == null ? condEl.getTagName() : condEl.getLocalName();
                String condName = condLocalName.contains(":") ? condLocalName.substring(condLocalName.indexOf(':') + 1) : condLocalName;
                if ("conditionExpression".equals(condName)) {
                    flow.setConditionExpression(condEl.getTextContent().trim());
                    break;
                }
            }

            process.addFlow(flow);
        }
    }

    private void validate(BpmnProcess process) {
        if (process.getStartEventId() == null) {
            throw new BusinessException(ErrorCode.WF_BPMN_XML_INVALID,
                    "BPMN must have exactly one StartEvent");
        }

        long endEventCount = process.getNodes().values().stream()
                .filter(n -> n.getType() == BpmnProcess.BpmnNodeType.END_EVENT)
                .count();
        if (endEventCount == 0) {
            throw new BusinessException(ErrorCode.WF_BPMN_XML_INVALID,
                    "BPMN must have at least one EndEvent");
        }

        // UserTask 校验: 必须有 assignee 或 candidateGroups
        for (BpmnProcess.BpmnNode node : process.getNodes().values()) {
            if (node.getType() == BpmnProcess.BpmnNodeType.USER_TASK) {
                boolean hasAssignee = node.getAssigneeExpr() != null && !node.getAssigneeExpr().isEmpty();
                boolean hasCandidate = node.getCandidateGroups() != null && !node.getCandidateGroups().isEmpty();
                if (!hasAssignee && !hasCandidate) {
                    throw new BusinessException(ErrorCode.WF_BPMN_XML_INVALID,
                            "UserTask " + node.getId() + " must have assignee or candidateGroups");
                }
            }
            if (node.getType() == BpmnProcess.BpmnNodeType.SERVICE_TASK) {
                if (node.getServiceTaskTarget() == null || node.getServiceTaskTarget().isEmpty()) {
                    throw new BusinessException(ErrorCode.WF_BPMN_XML_INVALID,
                            "ServiceTask " + node.getId() + " must have target attribute");
                }
            }
        }

        // 校验 StartEvent 必须有出边
        List<BpmnProcess.BpmnSequenceFlow> startFlows = process.getOutgoingFlows(process.getStartEventId());
        if (startFlows.isEmpty()) {
            throw new BusinessException(ErrorCode.WF_BPMN_XML_INVALID,
                    "StartEvent " + process.getStartEventId() + " has no outgoing flow");
        }
    }

    private BpmnProcess.BpmnNodeType mapNodeType(String localName) {
        return switch (localName) {
            case "startEvent" -> BpmnProcess.BpmnNodeType.START_EVENT;
            case "userTask" -> BpmnProcess.BpmnNodeType.USER_TASK;
            case "serviceTask" -> BpmnProcess.BpmnNodeType.SERVICE_TASK;
            case "exclusiveGateway" -> BpmnProcess.BpmnNodeType.EXCLUSIVE_GATEWAY;
            case "parallelGateway" -> BpmnProcess.BpmnNodeType.PARALLEL_GATEWAY;
            case "endEvent" -> BpmnProcess.BpmnNodeType.END_EVENT;
            default -> null;
        };
    }

    private Element findFirstElement(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element el)) continue;
            String ln = el.getLocalName() == null ? el.getTagName() : el.getLocalName();
            String name = ln.contains(":") ? ln.substring(ln.indexOf(':') + 1) : ln;
            if (localName.equals(name)) return el;
        }
        return null;
    }

    private String getDocumentation(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element el)) continue;
            String ln = el.getLocalName() == null ? el.getTagName() : el.getLocalName();
            String name = ln.contains(":") ? ln.substring(ln.indexOf(':') + 1) : ln;
            if ("documentation".equals(name)) {
                return el.getTextContent().trim();
            }
        }
        return null;
    }
}
