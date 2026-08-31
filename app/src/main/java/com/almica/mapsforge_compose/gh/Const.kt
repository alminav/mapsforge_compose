package com.almica.mapsforge_compose.gh

object Const {
    const val HGT_FOLDER_NAME = "hgt"
    const val HGT_EXT = ".hgt"
    const val PREF_GH_FILEPATH = "pref_gh_filepath"
    const val DEFAULT_LOCOMOTION = "1.1"
    const val GERMANY = "germany"
    const val GH_TAG = "graphhopper"
    const val GH_ROOT_FOLDER = "gh"
    const val TIME_PATTERN_LONG: String = "yyMMdd_HHmmss"

    object Companion {
        object VehicleEncoding {
            const val CAR_ENCODING = "car"
            const val FOOT_ENCODING = "foot"
            const val BIKE_ENCODING = "bike"
            const val AIRPLANE_ENCODING = "airplane"
        }
        object WeightingEncoding {
            const val SHORT_ENCODING = "shortest"
            const val FAST_ENCODING = "fastest"
        }
    }
}
