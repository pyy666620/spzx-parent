package com.spzx.product.controller;



import com.spzx.common.core.domain.R;
import com.spzx.common.core.web.controller.BaseController;
import com.spzx.common.core.web.domain.AjaxResult;
import com.spzx.common.core.web.page.TableDataInfo;
import com.spzx.common.security.annotation.InnerAuth;
import com.spzx.product.api.domain.Brand;
import com.spzx.product.service.IBrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

//import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * http://localhost:9205/brand/list
 */
@Slf4j
@Tag(name="品牌管理")
@RequestMapping("/brand")
@RestController
public class BrandController extends BaseController {

    @Autowired
    private IBrandService brandService;

    /**
     * 带条件的分页查询
     * @param name
     * @return
     */
    @Operation(summary = "带条件的分页查询")
    @GetMapping("/list")
    public TableDataInfo getPageList(String name){
        //若依框架封装的一个分页启动器。它的底层调用的是 MyBatis 分页插件（PageHelper）。
        startPage();
        List<Brand> brandList = brandService.getPageList(name);
        return getDataTable(brandList);
    }

    /**
     * 添加或更新品牌
     * @param brand
     * @return
     */
    @Operation(summary = "添加或更新品牌")
    @RequestMapping(method = {RequestMethod.PUT,RequestMethod.POST})
    public AjaxResult saveOrUpdate(@RequestBody Brand brand){
        brandService.saveOrUpdate(brand);
        return success();
    }

    /**
     * 根据id查询品牌信息
     * @param id
     * @return
     */
    @Operation(summary = "根据id查询品牌信息")
    @GetMapping("/{id}")
    public AjaxResult getBrandById(@PathVariable Long id){
        Brand brand = brandService.getById(id);
        return AjaxResult.success(brand);
    }

    /**
     * 根据id删除品牌信息
     * @param id
     * @return
     */
    @Operation(summary = "根据id删除品牌信息")
    @DeleteMapping("/{id]")
    public AjaxResult deleteBrandById(@PathVariable Long id){
        brandService.removeById(id);
        return AjaxResult.success();
    }

    /**
     * 查询所有品牌
     * @return
     */
    @Operation(summary = "查询所有品牌")
    @GetMapping("/getBrandAll")
    public AjaxResult getBrandAll(){
        return AjaxResult.success(brandService.selectBrandAll());
    }

    //=====给前台系统使用接口================================================
    @InnerAuth
    @Operation(summary = "获取全部品牌")
    @GetMapping("getBrandAllList")
    public R<List<Brand>> getBrandAllList() {
        return R.ok(brandService.selectBrandAll());
    }

}
