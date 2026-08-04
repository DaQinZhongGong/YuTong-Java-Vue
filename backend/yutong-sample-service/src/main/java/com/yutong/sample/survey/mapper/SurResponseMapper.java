package com.yutong.sample.survey.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.survey.domain.SurResponse;
import org.apache.ibatis.annotations.Mapper;

/**
 * 答卷 Mapper。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单 (GA2-42)。
 */
@Mapper
public interface SurResponseMapper extends BaseMapper<SurResponse> {
}
