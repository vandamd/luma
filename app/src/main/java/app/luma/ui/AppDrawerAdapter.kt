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
import app.luma.data.AppEntryType
import app.luma.data.AppModel
import app.luma.data.PinnedAppEntry
import app.luma.data.Prefs
import app.luma.databinding.AdapterAppDrawerBinding
import app.luma.helper.performAppTapHapticFeedback
import app.luma.helper.performLongPressHapticFeedback
import java.text.Normalizer

data class AppDrawerConfig(
    val gravity: Int,
    val clickListener: (AppModel) -> Unit,
    val appLongPressListener: ((AppModel) -> Unit)? = null,
    val showPinnedIcon: Boolean = false,
)

class AppDrawerAdapter(
    private val context: Context,
    private val config: AppDrawerConfig,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
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
    private var appsList: MutableList<AppModel> = mutableListOf()
    private var filteredApps: MutableList<AppModel> = mutableListOf()
    private val normalizedNameCache = mutableMapOf<String, String>()
    private val prefs = Prefs.getInstance(context)
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private var pinnedSet: Set<PinnedAppEntry> = emptySet()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val binding = AdapterAppDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        val appModel = filteredApps[holder.absoluteAdapterPosition]
        (holder as AppViewHolder).bind(
            config.gravity,
            appModel,
            config.clickListener,
            config.appLongPressListener,
            config.showPinnedIcon,
            config.showPinnedIcon && isPinned(appModel),
        )
    }

    override fun getItemCount(): Int = filteredApps.size

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter =
        object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchChars = constraint.toString()
                val appFilteredList =
                    if (searchChars.isEmpty()) {
                        appsList
                    } else {
                        appsList.filter { app ->
                            appLabelMatches(app.displayName, searchChars)
                        }
                    }

                return FilterResults().apply {
                    values = appFilteredList
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(
                constraint: CharSequence?,
                results: FilterResults?,
            ) {
                filteredApps = (results?.values as? List<AppModel>)?.toMutableList() ?: mutableListOf()
                notifyDataSetChanged()
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
        appsList.forEach { app ->
            app.hasNotification = packages.contains(app.appPackage)
        }
        notifyDataSetChanged()
    }

    fun setAppList(appsList: MutableList<AppModel>) {
        normalizedNameCache.clear()
        this.appsList = appsList
        filteredApps = appsList.toMutableList()
        pinnedSet = prefs.pinnedApps.toSet()
        notifyDataSetChanged()
    }

    private fun isPinned(appModel: AppModel): Boolean {
        val serial = userManager.getSerialNumberForUser(appModel.user)
        return pinnedSet.contains(PinnedAppEntry(appModel.appPackage, appModel.appActivityName, serial))
    }

    class AppViewHolder(
        private val binding: AdapterAppDrawerBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            appLabelGravity: Int,
            appModel: AppModel,
            listener: (AppModel) -> Unit,
            appLongPressListener: ((AppModel) -> Unit)? = null,
            showPinnedIcon: Boolean = false,
            isPinned: Boolean = false,
        ) {
            val context = itemView.context
            configureAppTitle(context, appModel, appLabelGravity, showPinnedIcon, isPinned)
            setupClickListeners(context, appModel, listener, appLongPressListener)
        }

        private fun configureAppTitle(
            context: Context,
            appModel: AppModel,
            gravity: Int,
            showPinnedIcon: Boolean,
            isPinned: Boolean,
        ) {
            val showIndicator = Prefs.getInstance(context).showNotificationIndicator && appModel.hasNotification
            val displayName = if (showIndicator) "${appModel.displayName}*" else appModel.displayName

            binding.appTitle.text = displayName

            val params = binding.appTitle.layoutParams as FrameLayout.LayoutParams
            params.gravity = gravity
            binding.appTitle.layoutParams = params

            val startIconRes =
                when {
                    appModel.entryType == AppEntryType.Tool -> R.drawable.tool_bulb_24px
                    showPinnedIcon && isPinned -> R.drawable.pin_24px
                    else -> 0
                }
            val endIconRes =
                if (appModel.entryType == AppEntryType.Tool && showPinnedIcon && isPinned) {
                    R.drawable.pin_24px
                } else {
                    0
                }
            binding.appTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(startIconRes, 0, endIconRes, 0)
        }

        private fun setupClickListeners(
            context: Context,
            appModel: AppModel,
            listener: (AppModel) -> Unit,
            appLongPressListener: ((AppModel) -> Unit)? = null,
        ) {
            binding.appTitleFrame.isHapticFeedbackEnabled = false
            binding.appTitleFrame.setOnClickListener {
                performAppTapHapticFeedback(context)
                listener(appModel)
            }
            if (appLongPressListener != null) {
                binding.appTitleFrame.setOnLongClickListener {
                    performLongPressHapticFeedback(context)
                    appLongPressListener(appModel)
                    true
                }
            } else {
                binding.appTitleFrame.setOnLongClickListener(null)
            }
        }
    }
}
