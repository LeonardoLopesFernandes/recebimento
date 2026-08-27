package io.recebimento.adapters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import io.recebimento.R

class FotosAdapter(
    private val onClick: (String) -> Unit,
    private val onLongClick: (String) -> Unit
) : RecyclerView.Adapter<FotosAdapter.VH>() {

    private val fotos = mutableListOf<String>()

    fun submitList(list: List<String>) {
        fotos.clear()
        fotos.addAll(list)
        notifyDataSetChanged()
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivFoto: ImageView = itemView.findViewById(R.id.ivFoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_foto, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = fotos.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val caminho = fotos[position]
        holder.ivFoto.setImageBitmap(carregarBitmap(caminho, 220))
        holder.itemView.setOnClickListener { onClick(caminho) }
        holder.itemView.setOnLongClickListener {
            onLongClick(caminho)
            true
        }
    }

    private fun carregarBitmap(caminho: String, tamanho: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(caminho, opts)
            var sample = 1
            while (opts.outWidth / sample > tamanho * 2 && opts.outHeight / sample > tamanho * 2) {
                sample *= 2
            }
            BitmapFactory.decodeFile(caminho, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }
}
