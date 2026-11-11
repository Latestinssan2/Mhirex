package com.mhirex.editor.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.mhirex.editor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class PixabayMediaAdapter(
    private val onAdd: (PixabayMediaHit) -> Unit,
    private val onSource: (PixabayMediaHit) -> Unit,
) : ListAdapter<PixabayMediaHit, PixabayMediaAdapter.Holder>(Diff) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_pixabay_media, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: Holder) {
        holder.imageJob?.cancel()
        holder.preview.tag = null
        holder.preview.setImageDrawable(ColorDrawable(Color.rgb(48, 51, 61)))
        super.onViewRecycled(holder)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        scope.cancel()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val preview: ImageView = itemView.findViewById(R.id.ivPixabayPreview)
        private val tags: TextView = itemView.findViewById(R.id.tvPixabayTags)
        private val creator: TextView = itemView.findViewById(R.id.tvPixabayCreator)
        private val add: MaterialButton = itemView.findViewById(R.id.btnPixabayAdd)
        private val source: TextView = itemView.findViewById(R.id.btnPixabaySource)
        var imageJob: Job? = null

        fun bind(hit: PixabayMediaHit) {
            tags.text = hit.tags
            creator.text = "Pixabay · by ${hit.creator}"
            itemView.setOnClickListener { onAdd(hit) }
            add.setOnClickListener { onAdd(hit) }
            source.setOnClickListener { onSource(hit) }
            val url = hit.previewUrl
            preview.tag = url
            preview.setImageDrawable(ColorDrawable(Color.rgb(48, 51, 61)))
            imageJob?.cancel()
            if (!url.startsWith("https://cdn.pixabay.com/")) return
            imageJob = scope.launch {
                val bitmap = withContext(Dispatchers.IO) { loadPreview(url) }
                if (preview.tag == url && bitmap != null) preview.setImageBitmap(bitmap)
            }
        }
    }

    private fun loadPreview(address: String): Bitmap? = runCatching {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private companion object {
        val Diff = object : DiffUtil.ItemCallback<PixabayMediaHit>() {
            override fun areItemsTheSame(oldItem: PixabayMediaHit, newItem: PixabayMediaHit) =
                oldItem.id == newItem.id && oldItem.pageUrl == newItem.pageUrl

            override fun areContentsTheSame(oldItem: PixabayMediaHit, newItem: PixabayMediaHit) = oldItem == newItem
        }
    }
}
