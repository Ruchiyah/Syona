package com.example.syona.ui.onboarding

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.syona.databinding.ItemOnboardingPageBinding
import kotlin.math.min

class OnboardingAdapter(
    private val items: List<OnboardingPage>
) : RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OnboardingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class OnboardingViewHolder(
        private val binding: ItemOnboardingPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: OnboardingPage) {
            binding.titleView.text = page.title
            binding.descriptionView.text = page.description

            binding.heroImage.setImageDrawable(null)
            binding.iconImage.setImageResource(page.iconResId)

            binding.root.post {
                loadScaledImage(page.imageResId)
            }
        }
        
        private fun loadScaledImage(imageResId: Int) {
            try {
                val context = binding.root.context ?: return
                val resources = context.resources ?: return
                
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeResource(resources, imageResId, options)
                
                if (options.outWidth <= 0 || options.outHeight <= 0) return

                val displayMetrics = resources.displayMetrics
                val targetWidth = displayMetrics.widthPixels
                val targetHeight = displayMetrics.heightPixels
                
                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                options.inMutable = false
                
                val bitmap = BitmapFactory.decodeResource(resources, imageResId, options)
                
                if (bitmap != null && !bitmap.isRecycled) {
                    binding.heroImage.setImageBitmap(bitmap)
                }
            } catch (e: OutOfMemoryError) {
                try {
                    val context = binding.root.context ?: return
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 8
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        inMutable = false
                    }
                    val bitmap = BitmapFactory.decodeResource(context.resources, imageResId, options)
                    if (bitmap != null && !bitmap.isRecycled) {
                        binding.heroImage.setImageBitmap(bitmap)
                    }
                } catch (e2: Exception) {
                    // Fallback failed, skip image
                }
            } catch (e: Exception) {
                // Image loading failed, skip
            }
        }
        
        private fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            val (height: Int, width: Int) = options.run { outHeight to outWidth }
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2

                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            return inSampleSize
        }
    }
}
