package com.yutong.sample.ticket.dto;

import lombok.Data;

/**
 * 工单状态流转操作请求。
 * action 取值: ASSIGN/ACCEPT/TRANSFER/SUSPEND/RESUME/RESOLVE/REOPEN/CLOSE/EVALUATE
 */
@Data
public class TicketActionRequest {

    /** 操作动作 */
    private String action;

    /** 派单/转单时的处理人 ID（ASSIGN/TRANSFER 必填） */
    private String handlerId;

    /** 处理人姓名快照（可选，默认从当前用户上下文取） */
    private String handlerName;

    /** 操作备注 */
    private String comment;

    /** 评价分数 1-5（EVALUATE 必填） */
    private Integer satisfactionScore;

    /** 评价内容（EVALUATE 可选） */
    private String satisfactionComment;
}
