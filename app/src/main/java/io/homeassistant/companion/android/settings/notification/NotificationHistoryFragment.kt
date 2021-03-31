package io.homeassistant.companion.android.settings.notification

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html.fromHtml
import android.view.Menu
import android.view.MenuItem
import android.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.notification.NotificationItem
import java.util.Calendar
import java.util.GregorianCalendar

class NotificationHistoryFragment : PreferenceFragmentCompat() {

    companion object {
        private var filterValue = "last25"
        fun newInstance(): NotificationHistoryFragment {
            return NotificationHistoryFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.setGroupVisible(R.id.notification_toolbar_group, true)

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/notifications/notifications-basic"))
        }
        menu.findItem(R.id.last25)?.title = getString(R.string.last_num_notifications, 25)
        menu.findItem(R.id.last50)?.title = getString(R.string.last_num_notifications, 50)
        menu.findItem(R.id.last100)?.title = getString(R.string.last_num_notifications, 100)

        val prefCategory = findPreference<PreferenceCategory>("list_notifications")
        val notificationDao = AppDatabase.getInstance(requireContext()).notificationDao()
        val allNotifications = notificationDao.getAll()

        if (allNotifications.isNullOrEmpty()) {
            menu.removeItem(R.id.search_notifications)
            menu.removeItem(R.id.notification_filter)
            menu.removeItem(R.id.action_delete)
        } else {
            val searchViewItem = menu.findItem(R.id.search_notifications)
            val searchView: SearchView = MenuItemCompat.getActionView(searchViewItem) as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchView.clearFocus()

                    return false
                }

                override fun onQueryTextChange(query: String?): Boolean {
                    var searchList: Array<NotificationItem> = emptyArray()
                    if (!query.isNullOrEmpty()) {
                        for (item in allNotifications) {
                            if (item.message.contains(query, true))
                                searchList += item
                        }
                        prefCategory?.title = getString(R.string.search_results)
                        reloadNotifications(searchList, prefCategory)
                    } else if (query.isNullOrEmpty()) {
                        prefCategory?.title = getString(R.string.notifications)
                        filterNotifications(filterValue, notificationDao, prefCategory)
                    }
                    return false
                }
            })
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val prefCategory = findPreference<PreferenceCategory>("list_notifications")
        val notificationDao = AppDatabase.getInstance(requireContext()).notificationDao()
        if (item.itemId in listOf(R.id.last25, R.id.last50, R.id.last100)) {
            filterValue = when (item.itemId) {
                R.id.last25 -> "last25"
                R.id.last50 -> "last50"
                R.id.last100 -> "last100"
                else -> "last25"
            }
            item.isChecked = !item.isChecked
            filterNotifications(filterValue, notificationDao, prefCategory)
        } else if (item.itemId == R.id.action_delete)
            deleteAllConfirmation(notificationDao)
        return super.onOptionsItemSelected(item)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.notifications, rootKey)
    }

    override fun onResume() {
        super.onResume()

        val notificationDao = AppDatabase.getInstance(requireContext()).notificationDao()
        val notificationList = notificationDao.getLastItems(25)

        val prefCategory = findPreference<PreferenceCategory>("list_notifications")
        if (!notificationList.isNullOrEmpty()) {
            prefCategory?.isVisible = true
            reloadNotifications(notificationList, prefCategory)
        } else {
            findPreference<PreferenceCategory>("list_notifications")?.let {
                it.isVisible = false
            }
            findPreference<Preference>("no_notifications")?.let {
                it.isVisible = true
            }
        }
    }

    private fun deleteAllConfirmation(notificationDao: NotificationDao) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())

        builder.setTitle(R.string.confirm_delete_all_notification_title)
        builder.setMessage(R.string.confirm_delete_all_notification_message)

        builder.setPositiveButton(
            R.string.confirm_positive
        ) { dialog, _ ->
            notificationDao.deleteAll()
            dialog.dismiss()
            parentFragmentManager.popBackStack()
        }

        builder.setNegativeButton(
            R.string.confirm_negative
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert: AlertDialog? = builder.create()
        alert?.show()
    }

    private fun reloadNotifications(notificationList: Array<NotificationItem>?, prefCategory: PreferenceCategory?) {
        prefCategory?.removeAll()
        if (notificationList != null) {
            for (item in notificationList) {
                val pref = Preference(preferenceScreen.context)
                val cal: Calendar = GregorianCalendar()
                cal.timeInMillis = item.received
                pref.key = item.id.toString()
                pref.title = cal.time.toString()
                pref.summary = fromHtml(item.message)
                pref.isIconSpaceReserved = false

                pref.setOnPreferenceClickListener {
                    parentFragmentManager
                        .beginTransaction()
                        .replace(
                            R.id.content,
                            NotificationDetailFragment.newInstance(
                                item
                            )
                        )
                        .addToBackStack("Notification Detail")
                        .commit()
                    return@setOnPreferenceClickListener true
                }

                prefCategory?.addPreference(pref)
            }
        }
    }

    private fun filterNotifications(filterValue: String?, notificationDao: NotificationDao, prefCategory: PreferenceCategory?) {
        when (filterValue) {
            "last25" -> {
                val notificationList = notificationDao.getLastItems(25)
                reloadNotifications(notificationList, prefCategory)
            }
            "last50" -> {
                val newList = notificationDao.getLastItems(50)
                reloadNotifications(newList, prefCategory)
            }
            "last100" -> {
                val newList = notificationDao.getLastItems(100)
                reloadNotifications(newList, prefCategory)
            }
            else -> {
                val notificationList = notificationDao.getLastItems(25)
                reloadNotifications(notificationList, prefCategory)
            }
        }
    }
}
