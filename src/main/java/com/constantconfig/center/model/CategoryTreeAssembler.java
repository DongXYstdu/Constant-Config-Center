package com.constantconfig.center.model;

import com.constantconfig.center.model.view.CategoryRespVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类树组装器
 *
 * <p>基于平铺的分类读视图列表，按 {@code categoryParentId} 原地挂载子分类，返回根分类列表。
 * 不依赖数据是否有序（先构造 id 索引，再二次遍历挂载），输入端 {@code children} 会被原地填充。</p>
 *
 * <p><b>职责边界</b>：纯函数、无副作用；只做「平铺 → 树」的挂载，不感知存储 DO 与持久化细节。</p>
 */
public final class CategoryTreeAssembler {

    private CategoryTreeAssembler() {
    }

    /**
     * 组装分类树。
     *
     * @param all 平铺分类读视图列表（可能为空）
     * @return 根分类列表（其 {@code children} 已按层级挂好），保持输入顺序
     */
    public static List<CategoryRespVO> assemble(List<CategoryRespVO> all) {
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, CategoryRespVO> byId = new LinkedHashMap<>(all.size());
        for (CategoryRespVO category : all) {
            byId.put(category.getCategoryId(), category);
        }
        List<CategoryRespVO> roots = new ArrayList<>();
        for (CategoryRespVO category : all) {
            CategoryRespVO parent = byId.get(category.getCategoryParentId());
            if (parent != null) {
                parent.getChildren().add(category);
            } else {
                roots.add(category);
            }
        }
        return roots;
    }
}