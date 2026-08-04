package com.yutong.ai.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.governance.domain.AiFeedback;
import org.apache.ibatis.annotations.Mapper;

/** AI 用户反馈 Mapper。设计来源: 37-AI治理与评测设计 人工反馈闭环章节 */
@Mapper
public interface AiFeedbackMapper extends BaseMapper<AiFeedback> {
}
