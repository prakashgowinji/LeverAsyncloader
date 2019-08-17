package com.pinload

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.pin.lever.Lever
import com.pinload.datamodel.Category
import com.pinload.datamodel.ItemInfo


class ItemListAdapter(val items: List<ItemInfo>) : RecyclerView.Adapter<ItemListAdapter.ViewHolder>() {

    // This method is returning the view for each item in the list
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemListAdapter.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)
        return ViewHolder(v)
    }

    // This method is binding the data on the list
    override fun onBindViewHolder(holder: ItemListAdapter.ViewHolder, position: Int) {
        holder.bindItems(items[position])
    }

    // This method is giving the size of the list
    override fun getItemCount(): Int {
        return items.size
    }

    // The class is holding the list item
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bindItems(itemInfo: ItemInfo) {
            val textId = itemView.findViewById(R.id.id) as TextView
            val textViewName = itemView.findViewById(R.id.name) as TextView
            val textViewusername  = itemView.findViewById(R.id.username) as TextView
            val textViewCategory  = itemView.findViewById(R.id.categories) as TextView
            val createdAt  = itemView.findViewById(R.id.datetime) as TextView
            val profileImage  = itemView.findViewById(R.id.profileImage) as ImageView
            Lever.instance().load(itemInfo.user.profileImage.large).into(profileImage);
            textViewName.text = itemInfo.user.name
            textId.text = itemInfo.user.id
            textViewusername.text = itemInfo.user.userName
            createdAt.text = itemInfo.createdAt
            textViewCategory.text = getCategories(itemInfo.categories)
        }

        fun getCategories(categories: List<Category>): String{
            var category = ""
            if(categories.size > 0){
                for(item in categories) {
                    category += item.title + ", "
                }
            }
            return "Categories: " + category

        }
    }
}