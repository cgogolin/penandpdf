;; The 'nil' configuration applies to all modes.
((nil . ((indent-tabs-mode . nil)
		 (tab-width . 4)
         (compile-command . "cd ~/src/android/mupdf/platform/android && ant clean && ~/src/android/android-ndk-r9/ndk-build clean && ~/src/android/android-ndk-r9/ndk-build && ant debug && /home/cgogolin/bin/adb install -r ~/src/android/mupdf/platform/android/bin/PenAndPDF-debug.apk")))
 (sgml-mode . ((indent-tabs-mode . t)
	 (tab-width . 4))))
