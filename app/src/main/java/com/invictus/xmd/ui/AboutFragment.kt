package com.invictus.xmd.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.invictus.xmd.BuildConfig
import com.invictus.xmd.R

/**
 * App identity, version, GitHub link, AGPL-3.0 license notice, the app's
 * developers, and a list of the open-source libraries Xmd is built on.
 * New screen -- no prior dialog equivalent existed. Credited libraries and
 * their versions are read from what app/build.gradle.kts actually declares
 * (see the `implementation(...)` block); the yt-dlp wrapper is only pulled
 * in on the full flavor, so it's only credited when
 * [BuildConfig.HAS_YOUTUBE_SUPPORT] is true.
 */
class AboutFragment : Fragment() {

    private data class Credit(val name: String, val descriptionRes: Int)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_about, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.aboutVersion).text =
            getString(R.string.about_version_format, BuildConfig.VERSION_NAME)

        view.findViewById<View>(R.id.aboutGithubRow).setOnClickListener {
            val url = getString(R.string.about_github_url)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        val developers = listOf(
            "Utsav Rajput" to "Developer",
            "Arnab Sadhukhan" to "Developer",
            "Ritesh Pandit" to "Developer",
        )
        val developersList = view.findViewById<LinearLayout>(R.id.aboutDevelopersList)
        val inflater = LayoutInflater.from(requireContext())
        developers.forEach { (name, role) ->
            val row = inflater.inflate(R.layout.item_about_credit, developersList, false)
            row.findViewById<TextView>(R.id.creditName).text = name
            row.findViewById<TextView>(R.id.creditDesc).text = role
            developersList.addView(row)
        }

        val credits = buildList {
            add(Credit("libtorrent4j", R.string.about_credit_libtorrent_desc))
            if (BuildConfig.HAS_YOUTUBE_SUPPORT) {
                add(Credit("yt-dlp (youtubedl-android)", R.string.about_credit_ytdlp_desc))
            }
            add(Credit("OkHttp", R.string.about_credit_okhttp_desc))
            add(Credit("jsoup", R.string.about_credit_jsoup_desc))
            add(Credit("Room", R.string.about_credit_room_desc))
            add(Credit("Kotlin Coroutines", R.string.about_credit_coroutines_desc))
        }

        val creditsList = view.findViewById<LinearLayout>(R.id.aboutCreditsList)
        credits.forEach { credit ->
            val row = inflater.inflate(R.layout.item_about_credit, creditsList, false)
            row.findViewById<TextView>(R.id.creditName).text = credit.name
            row.findViewById<TextView>(R.id.creditDesc).setText(credit.descriptionRes)
            creditsList.addView(row)
        }
    }
}
