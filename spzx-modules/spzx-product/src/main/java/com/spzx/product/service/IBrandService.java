package com.spzx.product.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.spzx.product.api.domain.Brand;

import java.util.List;


public interface IBrandService extends IService<Brand> {

    List<Brand> getPageList(String name);

    List<Brand> selectBrandAll();
}
