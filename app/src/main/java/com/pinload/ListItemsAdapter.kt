package com.pinload

import android.support.v7.widget.CardView
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.pin.lever.Lever
import com.pin.lever.utils.getMillisFromString
import com.pin.lever.utils.getRelativeTimeSpan
import com.pinload.databinding.ItemLayoutBinding
import com.pinload.datamodel.Category
import com.pinload.datamodel.ItemInfo
import com.pinload.utils.ListItemClickListener
import kotlinx.android.synthetic.main.item_layout.view.*

class ListItemsAdapter(var itemInfoList: MutableList<ItemInfo>,
                       private val listItemClickListener: ListItemClickListener
) :
    RecyclerView.Adapter<ListItemsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ItemLayoutBinding.inflate(layoutInflater, parent, false)
        return ViewHolder(binding.root)
    }

    override fun getItemCount(): Int {
        return itemInfoList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        var itemInfo = itemInfoList.get(position)
        holder.txtId.text = itemInfo.user.id
        Lever.instance()!!.load(itemInfo.user.profileImage.large).into(holder.imgProfile)
        holder.txtName.text = itemInfo.user.name
        holder.txtUserName.text = itemInfo.user.userName
        holder.txtDate.text = getRelativeTimeSpan(getMillisFromString(itemInfo.createdAt))
        holder.txtCategories.text = getCategories(itemInfo.categories)
        holder.itemLayout.setOnClickListener {
            listItemClickListener.onItemClick(position)
        }
    }

    private fun getCategories(categories: List<Category>): String {
        var category = ""
        if (categories.isNotEmpty()) {
            for (item in categories) {
                category += item.title + ", "
            }
        }
        return "Categories: $category"

    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtId: TextView = view.txtId
        val txtName: TextView = view.name
        val txtCategories: TextView = view.categories
        val txtUserName: TextView = view.username
        val txtDate: TextView = view.datetime
        val imgProfile: ImageView = view.profileImage
        val itemLayout: CardView = view.itemLayout
    }
}