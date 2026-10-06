plugins { `java-library` }

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.7")
}
