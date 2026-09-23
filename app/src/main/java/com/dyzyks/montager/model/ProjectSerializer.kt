package com.dyzyks.montager.model

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ProjectSerializer {

    fun toJson(project: Project): JSONObject {
        val root = JSONObject()
        root.put("id", project.id)
        root.put("schemaVersion", project.schemaVersion)
        root.put("name", project.name)
        root.put("width", project.width)
        root.put("height", project.height)
        root.put("fps", project.fps)
        root.put("createdAt", project.createdAt)
        root.put("modifiedAt", project.modifiedAt)

        val tracksArray = JSONArray()
        for (track in project.tracks) {
            val trackObj = JSONObject()
            trackObj.put("id", track.id)
            trackObj.put("name", track.name)
            trackObj.put("type", track.type.name)
            trackObj.put("isMuted", track.isMuted)
            trackObj.put("isSolo", track.isSolo)
            trackObj.put("isLocked", track.isLocked)

            val clipsArray = JSONArray()
            for (clip in track.clips) {
                val clipObj = JSONObject()
                clipObj.put("id", clip.id)
                clipObj.put("name", clip.name)
                clipObj.put("sourceUri", clip.sourceUri)
                clipObj.put("mediaType", clip.mediaType.name)
                clipObj.put("timelineStartUs", clip.timelineStartUs)
                clipObj.put("durationUs", clip.durationUs)
                clipObj.put("sourceInUs", clip.sourceInUs)
                clipObj.put("sourceOutUs", clip.sourceOutUs)
                clipObj.put("speed", clip.speed.toDouble())
                clipObj.put("isSelected", clip.isSelected)
                clip.groupId?.let { clipObj.put("groupId", it) }

                // Transform
                val transformObj = JSONObject()
                transformObj.put("positionX", clip.transform.positionX.toDouble())
                transformObj.put("positionY", clip.transform.positionY.toDouble())
                transformObj.put("scaleX", clip.transform.scaleX.toDouble())
                transformObj.put("scaleY", clip.transform.scaleY.toDouble())
                transformObj.put("rotationDeg", clip.transform.rotationDeg.toDouble())
                transformObj.put("opacity", clip.transform.opacity.toDouble())
                val kfArray = JSONArray()
                for (kf in clip.transform.keyframes) {
                    val kfObj = JSONObject()
                    kfObj.put("timeUs", kf.timeUs)
                    kfObj.put("value", kf.value.toDouble())
                    kfObj.put("easing", kf.easing.name)
                    kfArray.put(kfObj)
                }
                transformObj.put("keyframes", kfArray)
                clipObj.put("transform", transformObj)

                // Color Grading
                val cgObj = JSONObject()
                cgObj.put("brightness", clip.colorGrading.brightness.toDouble())
                cgObj.put("contrast", clip.colorGrading.contrast.toDouble())
                cgObj.put("saturation", clip.colorGrading.saturation.toDouble())
                cgObj.put("temperature", clip.colorGrading.temperature.toDouble())
                cgObj.put("tint", clip.colorGrading.tint.toDouble())
                cgObj.put("exposure", clip.colorGrading.exposure.toDouble())
                cgObj.put("gamma", clip.colorGrading.gamma.toDouble())
                clipObj.put("colorGrading", cgObj)

                // Audio Settings
                val audioObj = JSONObject()
                audioObj.put("volume", clip.audioSettings.volume.toDouble())
                audioObj.put("gainDb", clip.audioSettings.gainDb.toDouble())
                audioObj.put("pan", clip.audioSettings.pan.toDouble())
                audioObj.put("fadeInUs", clip.audioSettings.fadeInUs)
                audioObj.put("fadeOutUs", clip.audioSettings.fadeOutUs)
                audioObj.put("isMuted", clip.audioSettings.isMuted)
                audioObj.put("isSolo", clip.audioSettings.isSolo)
                clipObj.put("audioSettings", audioObj)

                // Effects
                val effectsArray = JSONArray()
                for (eff in clip.effects) {
                    val effObj = JSONObject()
                    effObj.put("id", eff.id)
                    effObj.put("type", eff.type.name)
                    effObj.put("startTimeUs", eff.startTimeUs)
                    effObj.put("durationUs", eff.durationUs)
                    effObj.put("intensity", eff.intensity.toDouble())
                    val paramsObj = JSONObject()
                    for ((k, v) in eff.parameters) {
                        paramsObj.put(k, v.toDouble())
                    }
                    effObj.put("parameters", paramsObj)
                    effectsArray.put(effObj)
                }
                clipObj.put("effects", effectsArray)

                // Transitions
                clip.transitionIn?.let { tr ->
                    val trObj = JSONObject()
                    trObj.put("id", tr.id)
                    trObj.put("type", tr.type.name)
                    trObj.put("durationUs", tr.durationUs)
                    trObj.put("alignment", tr.alignment.name)
                    clipObj.put("transitionIn", trObj)
                }
                clip.transitionOut?.let { tr ->
                    val trObj = JSONObject()
                    trObj.put("id", tr.id)
                    trObj.put("type", tr.type.name)
                    trObj.put("durationUs", tr.durationUs)
                    trObj.put("alignment", tr.alignment.name)
                    clipObj.put("transitionOut", trObj)
                }

                // Text Overlay
                clip.textOverlay?.let { txt ->
                    val txtObj = JSONObject()
                    txtObj.put("id", txt.id)
                    txtObj.put("text", txt.text)
                    txtObj.put("fontSizeSp", txt.fontSizeSp.toDouble())
                    txtObj.put("textColorArgb", txt.textColorArgb)
                    txtObj.put("backgroundColorArgb", txt.backgroundColorArgb)
                    txtObj.put("positionX", txt.positionX.toDouble())
                    txtObj.put("positionY", txt.positionY.toDouble())
                    txtObj.put("scale", txt.scale.toDouble())
                    txtObj.put("opacity", txt.opacity.toDouble())
                    txtObj.put("isBold", txt.isBold)
                    clipObj.put("textOverlay", txtObj)
                }

                clipsArray.put(clipObj)
            }
            trackObj.put("clips", clipsArray)
            tracksArray.put(trackObj)
        }
        root.put("tracks", tracksArray)
        return root
    }

    fun fromJson(json: JSONObject): Project {
        val loadedVersion = json.optInt("schemaVersion", 1)
        val migratedJson = if (loadedVersion < Project.CURRENT_SCHEMA_VERSION) {
            migrate(json, loadedVersion, Project.CURRENT_SCHEMA_VERSION)
        } else {
            json
        }

        val id = migratedJson.optString("id")
        val name = migratedJson.optString("name", "Untitled")
        val width = migratedJson.optInt("width", 1920)
        val height = migratedJson.optInt("height", 1080)
        val fps = migratedJson.optInt("fps", 60)
        val createdAt = migratedJson.optLong("createdAt", System.currentTimeMillis())
        val modifiedAt = migratedJson.optLong("modifiedAt", System.currentTimeMillis())

        val tracksArray = migratedJson.optJSONArray("tracks") ?: JSONArray()
        val tracks = mutableListOf<Track>()

        for (i in 0 until tracksArray.length()) {
            val trackObj = tracksArray.getJSONObject(i)
            val trackId = trackObj.getString("id")
            val trackName = trackObj.getString("name")
            val trackType = TrackType.valueOf(trackObj.optString("type", "VIDEO"))
            val isMuted = trackObj.optBoolean("isMuted", false)
            val isSolo = trackObj.optBoolean("isSolo", false)
            val isLocked = trackObj.optBoolean("isLocked", false)

            val clipsArray = trackObj.optJSONArray("clips") ?: JSONArray()
            val clips = mutableListOf<TimelineClip>()

            for (j in 0 until clipsArray.length()) {
                val clipObj = clipsArray.getJSONObject(j)
                val clipId = clipObj.getString("id")
                val clipName = clipObj.getString("name")
                val sourceUri = clipObj.getString("sourceUri")
                val mediaType = MediaType.valueOf(clipObj.optString("mediaType", "VIDEO"))
                val timelineStartUs = clipObj.optLong("timelineStartUs", 0L)
                val durationUs = clipObj.optLong("durationUs", 5_000_000L)
                val sourceInUs = clipObj.optLong("sourceInUs", 0L)
                val sourceOutUs = clipObj.optLong("sourceOutUs", 5_000_000L)
                val speed = clipObj.optDouble("speed", 1.0).toFloat()
                val isSelected = clipObj.optBoolean("isSelected", false)
                val groupId = if (clipObj.has("groupId")) clipObj.getString("groupId") else null

                // Transform
                val transform = clipObj.optJSONObject("transform")?.let { tObj ->
                    val kfList = mutableListOf<Keyframe>()
                    val kfArr = tObj.optJSONArray("keyframes") ?: JSONArray()
                    for (k in 0 until kfArr.length()) {
                        val kObj = kfArr.getJSONObject(k)
                        kfList.add(
                            Keyframe(
                                timeUs = kObj.getLong("timeUs"),
                                value = kObj.getDouble("value").toFloat(),
                                easing = EasingType.valueOf(kObj.optString("easing", "LINEAR"))
                            )
                        )
                    }
                    Transform(
                        positionX = tObj.optDouble("positionX", 0.0).toFloat(),
                        positionY = tObj.optDouble("positionY", 0.0).toFloat(),
                        scaleX = tObj.optDouble("scaleX", 1.0).toFloat(),
                        scaleY = tObj.optDouble("scaleY", 1.0).toFloat(),
                        rotationDeg = tObj.optDouble("rotationDeg", 0.0).toFloat(),
                        opacity = tObj.optDouble("opacity", 1.0).toFloat(),
                        keyframes = kfList
                    )
                } ?: Transform()

                // Color Grading
                val colorGrading = clipObj.optJSONObject("colorGrading")?.let { cgObj ->
                    ColorGrading(
                        brightness = cgObj.optDouble("brightness", 0.0).toFloat(),
                        contrast = cgObj.optDouble("contrast", 1.0).toFloat(),
                        saturation = cgObj.optDouble("saturation", 1.0).toFloat(),
                        temperature = cgObj.optDouble("temperature", 0.0).toFloat(),
                        tint = cgObj.optDouble("tint", 0.0).toFloat(),
                        exposure = cgObj.optDouble("exposure", 0.0).toFloat(),
                        gamma = cgObj.optDouble("gamma", 1.0).toFloat()
                    )
                } ?: ColorGrading()

                // Audio Settings
                val audioSettings = clipObj.optJSONObject("audioSettings")?.let { aObj ->
                    AudioSettings(
                        volume = aObj.optDouble("volume", 1.0).toFloat(),
                        gainDb = aObj.optDouble("gainDb", 0.0).toFloat(),
                        pan = aObj.optDouble("pan", 0.0).toFloat(),
                        fadeInUs = aObj.optLong("fadeInUs", 0L),
                        fadeOutUs = aObj.optLong("fadeOutUs", 0L),
                        isMuted = aObj.optBoolean("isMuted", false),
                        isSolo = aObj.optBoolean("isSolo", false)
                    )
                } ?: AudioSettings()

                // Effects
                val effects = mutableListOf<Effect>()
                val effArr = clipObj.optJSONArray("effects") ?: JSONArray()
                for (e in 0 until effArr.length()) {
                    val eObj = effArr.getJSONObject(e)
                    val paramsMap = mutableMapOf<String, Float>()
                    val pObj = eObj.optJSONObject("parameters")
                    pObj?.keys()?.forEach { key ->
                        paramsMap[key] = pObj.getDouble(key).toFloat()
                    }
                    effects.add(
                        Effect(
                            id = eObj.getString("id"),
                            type = EffectType.valueOf(eObj.getString("type")),
                            startTimeUs = eObj.optLong("startTimeUs", 0L),
                            durationUs = eObj.optLong("durationUs", 1_000_000L),
                            intensity = eObj.optDouble("intensity", 0.5).toFloat(),
                            parameters = paramsMap
                        )
                    )
                }

                // Transitions
                val transitionIn = clipObj.optJSONObject("transitionIn")?.let { trObj ->
                    Transition(
                        id = trObj.getString("id"),
                        type = TransitionType.valueOf(trObj.getString("type")),
                        durationUs = trObj.optLong("durationUs", 500_000L),
                        alignment = TransitionAlignment.valueOf(trObj.optString("alignment", "CENTER_ON_CUT"))
                    )
                }
                val transitionOut = clipObj.optJSONObject("transitionOut")?.let { trObj ->
                    Transition(
                        id = trObj.getString("id"),
                        type = TransitionType.valueOf(trObj.getString("type")),
                        durationUs = trObj.optLong("durationUs", 500_000L),
                        alignment = TransitionAlignment.valueOf(trObj.optString("alignment", "CENTER_ON_CUT"))
                    )
                }

                // Text Overlay
                val textOverlay = clipObj.optJSONObject("textOverlay")?.let { txtObj ->
                    TextOverlay(
                        id = txtObj.getString("id"),
                        text = txtObj.getString("text"),
                        fontSizeSp = txtObj.optDouble("fontSizeSp", 36.0).toFloat(),
                        textColorArgb = txtObj.optLong("textColorArgb", 0xFFFFFFFFL),
                        backgroundColorArgb = txtObj.optLong("backgroundColorArgb", 0x88000000L),
                        positionX = txtObj.optDouble("positionX", 0.0).toFloat(),
                        positionY = txtObj.optDouble("positionY", 0.0).toFloat(),
                        scale = txtObj.optDouble("scale", 1.0).toFloat(),
                        opacity = txtObj.optDouble("opacity", 1.0).toFloat(),
                        isBold = txtObj.optBoolean("isBold", true)
                    )
                }

                clips.add(
                    TimelineClip(
                        id = clipId,
                        name = clipName,
                        sourceUri = sourceUri,
                        mediaType = mediaType,
                        timelineStartUs = timelineStartUs,
                        durationUs = durationUs,
                        sourceInUs = sourceInUs,
                        sourceOutUs = sourceOutUs,
                        speed = speed,
                        transform = transform,
                        colorGrading = colorGrading,
                        audioSettings = audioSettings,
                        effects = effects,
                        transitionIn = transitionIn,
                        transitionOut = transitionOut,
                        textOverlay = textOverlay,
                        isSelected = isSelected,
                        groupId = groupId
                    )
                )
            }

            tracks.add(
                Track(
                    id = trackId,
                    name = trackName,
                    type = trackType,
                    clips = clips,
                    isMuted = isMuted,
                    isSolo = isSolo,
                    isLocked = isLocked
                )
            )
        }

        return Project(
            id = id,
            schemaVersion = Project.CURRENT_SCHEMA_VERSION,
            name = name,
            width = width,
            height = height,
            fps = fps,
            tracks = tracks,
            createdAt = createdAt,
            modifiedAt = modifiedAt
        )
    }

    /**
     * Schema migration logic from old versions up to target version.
     */
    fun migrate(json: JSONObject, fromVersion: Int, toVersion: Int): JSONObject {
        var current = json
        var v = fromVersion
        while (v < toVersion) {
            when (v) {
                1 -> {
                    // Migrate V1 -> V2: Add missing defaults for effects, color grading, transitions
                    val tracks = current.optJSONArray("tracks") ?: JSONArray()
                    for (i in 0 until tracks.length()) {
                        val track = tracks.getJSONObject(i)
                        val clips = track.optJSONArray("clips") ?: JSONArray()
                        for (j in 0 until clips.length()) {
                            val clip = clips.getJSONObject(j)
                            if (!clip.has("colorGrading")) {
                                clip.put("colorGrading", JSONObject().apply {
                                    put("brightness", 0.0)
                                    put("contrast", 1.0)
                                    put("saturation", 1.0)
                                })
                            }
                            if (!clip.has("effects")) {
                                clip.put("effects", JSONArray())
                            }
                        }
                    }
                    current.put("schemaVersion", 2)
                    v = 2
                }
                else -> {
                    current.put("schemaVersion", toVersion)
                    v = toVersion
                }
            }
        }
        return current
    }

    /**
     * Atomically saves the project JSON to disk using a temporary file and atomic file move.
     */
    fun saveToFileAtomic(project: Project, destinationFile: File) {
        val parent = destinationFile.parentFile ?: File(".")
        if (!parent.exists()) parent.mkdirs()

        val tempFile = File(parent, "${destinationFile.name}.${System.currentTimeMillis()}.tmp")
        val json = toJson(project)
        val jsonBytes = json.toString(2).toByteArray(Charsets.UTF_8)

        FileOutputStream(tempFile).use { fos ->
            fos.write(jsonBytes)
            fos.flush()
            fos.fd.sync()
        }

        // Atomically replace target file
        try {
            Files.move(
                tempFile.toPath(),
                destinationFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: Exception) {
            // Fallback for filesystems that do not support ATOMIC_MOVE across mounts
            Files.move(
                tempFile.toPath(),
                destinationFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    fun loadFromFile(file: File): Project {
        val content = file.readText(Charsets.UTF_8)
        return fromJson(JSONObject(content))
    }
}
