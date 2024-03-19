# conan-gradle-plugin

Simple gradle plugin for conan

# setup 

Install conan via pip

```bash
pip3 install conan
```

Copy profiles 

```bash
cp -r ./profiles ~/.conan2/
```

# Use

```groovy
apply plugin: 'com.android.application'
apply from: "${rootDir}/conan.gradle"

android {
    compileSdkVersion 29
    buildToolsVersion "29.0.1"

    defaultConfig {
        applicationId "org.libsdl.app"
        minSdkVersion 26
        targetSdkVersion 29
        versionCode 1
        versionName "1.0"
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags "-std=c++17"
                abiFilters 'x86', 'armeabi-v7a', 'x86_64'
            }
        }
    } //defaultConfig

    // https://stackoverflow.com/questions/36414219/install-failed-no-matching-abis-failed-to-extract-native-libraries-res-113
    splits {
        abi {
            enable true
            reset()
            include 'x86', 'armeabi-v7a', 'x86_64'
//            include defaultConfig.externalNativeBuild.getCmake().getAbiFilters().
            universalApk true
        }
    }

    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
            version "3.22.1"
        }
    }

} //android

clean.doLast {
    delete file('conan_build')
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```