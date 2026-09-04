package app.luma.ui

import android.content.Context
import android.os.UserManager
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import app.luma.R
import app.luma.data.AppDrawerItem
import app.luma.data.AppModel
import app.luma.data.FolderModel
import app.luma.data.PinnedAppEntry
import app.luma.data.Prefs
import app.luma.databinding.AdapterAppDrawerBinding
import app.luma.helper.performAppTapHapticFeedback
import app.luma.helper.performLongPressHapticFeedback
import java.text.Normalizer

data class AppDrawerConfig(
    val gravity: Int,
    val clickListener: (AppDrawerItem) -> Unit,
    val appLongPressListener: ((AppDrawerItem) -> Unit)? = null,
    val showPinnedIcon: Boolean = false,
)

class AppDrawerAdapter(
    private val context: Context,
    private val config: AppDrawerConfig,
) : RecyclerView.Adapter<AppDrawerAdapter.ViewHolder>(),
    Filterable {
    companion object {
        private val DIACRITICAL_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
        private val SEPARATOR_REGEX = Regex("[-_+,. ]")

        private fun normalizeForSearch(text: String): String =
            Normalizer
                .normalize(text, Normalizer.Form.NFD)
                .replace(DIACRITICAL_REGEX, "")
                .replace(SEPARATOR_REGEX, "")
    }

    private var appFilter = createAppFilter()
    private var itemsList: MutableList<AppDrawerItem> = mutableListOf()
    private var itemsFilteredList: MutableList<AppDrawerItem> = mutableListOf()
    private val normalizedNameCache = mutableMapOf<String, String>()
    private val prefs = Prefs.getInstance(context)
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private var pinnedSet: Set<PinnedAppEntry> = emptySet()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = AdapterAppDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        if (itemsFilteredList.isEmpty()) return
        val item = itemsFilteredList[holder.absoluteAdapterPosition]
        holder.bind(
            config.gravity,
            item,
            config.clickListener,
            config.appLongPressListener,
            config.showPinnedIcon,
            item is AppDrawerItem.App && isPinned(item.appModel),
        )
    }

    override fun getItemCount(): Int = itemsFilteredList.size

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchChars = constraint.toString()
                val itemsFilteredList =
                    if (searchChars.isEmpty()) {
                        itemsList
                    } else {
                        itemsList.filter { item ->
                            val displayName = item.label
                            appLabelMatches(displayName, searchChars)
                        }
                    }

                val filterResults = FilterResults()
                filterResults.values = itemsFilteredList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(
                constraint: CharSequence?,
                results: FilterResults?,
            ) {
                itemsFilteredList = (results?.values as? List<AppDrawerItem>)?.toMutableList() ?: mutableListOf()
                notifyDataSetChanged()
            }
        }
    }

    private fun appLabelMatches(
        appLabel: String,
        searchChars: String,
    ): Boolean {
        if (appLabel.contains(searchChars, ignoreCase = true)) return true
        val normalized = normalizedNameCache.getOrPut(appLabel) { normalizeForSearch(appLabel) }
        return normalized.contains(searchChars, ignoreCase = true)
    }

    fun updateNotifications(packages: Set<String>) {
        itemsFilteredList.forEachIndexed { i, item ->
            if (item is AppDrawerItem.App) {
                val app = item.appModel
                val had = app.hasNotification
                app.hasNotification = packages.contains(app.appPackage)
                if (had != app.hasNotification) notifyItemChanged(i)
            }
        }
        itemsList.forEach { item ->
            if (item is AppDrawerItem.App && itemsFilteredList.none { it === item }) {
                item.appModel.hasNotification = packages.contains(item.appModel.appPackage)
            }
        }
    }

    fun setItemList(itemsList: MutableList<AppDrawerItem>) {
        normalizedNameCache.clear()
        this.itemsList = itemsList
        this.itemsFilteredList = this.itemsList
        pinnedSet = prefs.pinnedApps.toSet()
        notifyDataSetChanged()
    }

    private fun isPinned(appModel: AppModel): Boolean {
        val serial = userManager.getSerialNumberForUser(appModel.user)
        return pinnedSet.contains(PinnedAppEntry(appModel.appPackage, appModel.appActivityName, serial))
    }

    class ViewHolder(
        val binding: AdapterAppDrawerBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            appLabelGravity: Int,
            item: AppDrawerItem,
            listener: (AppDrawerItem) -> Unit,
            appLongPressListener: ((AppDrawerItem) -> Unit)? = null,
            showPinnedIcon: Boolean = false,
            isPinned: Boolean = false,
        ) {
            val context = itemView.context
            configureTitle(context, item, appLabelGravity, showPinnedIcon, isPinned)
            setupClickListeners(context, item, listener, appLongPressListener)
        }

        private fun configureTitle(
            context: Context,
            item: AppDrawerItem,
            gravity: Int,
            showPinnedIcon: Boolean,
            isPinned: Boolean,
        ) {
            val name = item.label
            val hasNotification = item is AppDrawerItem.App && item.appModel.hasNotification
            val showIndicator = Prefs.getInstance(context).showNotificationIndicator && hasNotification
            val displayName = if (showIndicator) "$name*" else name

            binding.appTitle.text = displayName

            val params = binding.appTitle.layoutParams as FrameLayout.LayoutParams
            params.gravity = gravity
            binding.appTitle.layoutParams = params

            val iconRes = when {
                item is AppDrawerItem.Folder -> R.drawable.folder_24px
                showPinnedIcon && isPinned -> R.drawable.pin_24px
                else -> 0
            }
            binding.appTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
        }

        private fun setupClickListeners(
            context: Context,
            item: AppDrawerItem,
            listener: (AppDrawerItem) -> Unit,
            appLongPressListener: ((AppDrawerItem) -> Unit)? = null,
        ) {
            binding.appTitleFrame.isHapticFeedbackEnabled = false
            binding.appTitleFrame.setOnClickListener {
                performAppTapHapticFeedback(context)
                listener(item)
            }
            if (appLongPressListener != null) {
                binding.appTitleFrame.setOnLongClickListener {
                    performLongPressHapticFeedback(context)
                    appLongPressListener(item)
                    true
                }
            } else {
                binding.appTitleFrame.setOnLongClickListener(null)
            }
        }
    }
}
