package com.androzic.ui;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.androzic.R;

public class DrawerAdapter extends ArrayAdapter<DrawerItem>
{
	private static final int VIEW_TYPE_TITLE = 0;
	private static final int VIEW_TYPE_ACTION = 1;

	Context mContext;
	ArrayList<DrawerItem> mDrawerItems;

	public DrawerAdapter(Context mContext, ArrayList<DrawerItem> items)
	{

		super(mContext, R.layout.drawer_list_item, items);
		this.mContext = mContext;
		this.mDrawerItems = items;
	}

	@Override
	public boolean areAllItemsEnabled()
	{
		return false;
	}

	@Override
	public boolean isEnabled(int position)
	{
		return !getItem(position).isTitle();
	}

	@Override
	public int getViewTypeCount()
	{
		return 2;
	}

	@Override
	public int getItemViewType(int position)
	{
		DrawerItem item = mDrawerItems.get(position);
		if (item.isTitle())
		{
			return VIEW_TYPE_TITLE;
		}
		else
		{
			return VIEW_TYPE_ACTION;
		}
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		DrawerItemHolder drawerHolder;
		DrawerItem item = mDrawerItems.get(position);

		int type = getItemViewType(position);

		if (convertView == null)
		{
			LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
			drawerHolder = new DrawerItemHolder();
			if (type == VIEW_TYPE_TITLE)
			{
				convertView = inflater.inflate(R.layout.drawer_list_title, parent, false);
				drawerHolder.title = (TextView) convertView.findViewById(R.id.drawerTitle);
			}
			else if (type == VIEW_TYPE_ACTION)
			{
				convertView = inflater.inflate(R.layout.drawer_list_item, parent, false);
				drawerHolder.icon = (ImageView) convertView.findViewById(R.id.drawerIcon);
				drawerHolder.name = (TextView) convertView.findViewById(R.id.drawerName);
			}
			convertView.setTag(drawerHolder);
		}
		else
		{
			drawerHolder = (DrawerItemHolder) convertView.getTag();
		}

		if (type == VIEW_TYPE_TITLE)
		{
			drawerHolder.title.setText(item.name);
		}
		else if (type == VIEW_TYPE_ACTION)
		{
			drawerHolder.icon.setImageDrawable(item.icon);
			drawerHolder.name.setText(item.name);
		}

		return convertView;
	}

	private static class DrawerItemHolder
	{
		TextView title;
		TextView name;
		ImageView icon;
	}
}