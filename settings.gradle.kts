pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}

	resolutionStrategy {
		eachPlugin {
			if (requested.id.id == "org.junit.platform.gradle.plugin") {
				useModule("org.junit.platform:junit-platform-gradle-plugin:$requested.version")
			}
		}
	}
}

rootProject.name = "fluid-json"

enableFeaturePreview("STABLE_PUBLISHING")
