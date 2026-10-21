package com.example.stocks.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.stocks.model.MarketCategory;
import com.example.stocks.ui.QuoteFragment;

/**
 * ViewPager2 适配器：每个分类对应一个行情 Fragment。
 *
 * 说明：按 ViewPager2 的规范，createFragment 每次返回一个新的 Fragment 实例，
 * 搜索关键字通过 SearchQuery 单例下发，不在这里缓存 Fragment 引用
 * （Activity 持有的实例不一定是界面上的那个，屏幕旋转后尤其容易错位）。
 */
public class CategoryPagerAdapter extends FragmentStateAdapter {

    private final MarketCategory[] categories = MarketCategory.values();

    public CategoryPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position < 0 || position >= categories.length) {
            position = 0;
        }
        return QuoteFragment.newInstance(categories[position]);
    }

    @Override
    public int getItemCount() {
        return categories.length;
    }

    /** 获取指定位置对应的分类（用于设置 Tab 标题） */
    public MarketCategory getCategory(int position) {
        if (position < 0 || position >= categories.length) {
            return null;
        }
        return categories[position];
    }
}
