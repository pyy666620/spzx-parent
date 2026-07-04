package com.spzx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spzx.product.api.domain.Brand;


import java.util.List;


public interface BrandMapper extends BaseMapper<Brand> {
    /**
     * 带条件的分页查询
     * @param name
     * @return
     */
    List<Brand> getPageList(String name);

    /**
     * 查询所有
     * @return
     */
    List<Brand> selectBrandAll();
}
