package com.yutong.sample.drama.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.drama.domain.DramaCharacter;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DramaCharacterMapper extends BaseMapper<DramaCharacter> {
}
