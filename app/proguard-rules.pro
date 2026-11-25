# Glance identifies each widget by its GlanceAppWidget class name (GlanceAppWidgetManager
# stores receiver -> provider class). If R8 merges or renames SmallWidget and WideWidget, both
# receivers map to one class and every widget draws the same layout. Keep them distinct.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { <init>(); }
