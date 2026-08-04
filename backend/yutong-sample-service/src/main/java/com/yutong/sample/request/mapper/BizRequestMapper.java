package com.yutong.sample.request.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.request.domain.BizRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BizRequestMapper extends BaseMapper<BizRequest> {
}
