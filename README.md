# AbsoluteCinema 🎬

**AbsoluteCinema** is a Java-based movie discovery and personal watchlist application built with Java Swing. It allows users to explore movies, search for titles, and manage their personal movie watchlist through a desktop graphical interface.

## ✨ Features

- **Movie Discovery** — Explore movie information using The Movie Database (TMDB) API.
- **Movie Search** — Search for movies using TMDB.
- **Personal Watchlist** — View and manage your saved movies.
- **Add Movies Manually** — Add movies to your watchlist without searching online.
- **Watched Status** — Mark movies as watched.
- **Personal Ratings** — Rate movies based on your own preferences.
- **Poster Support** — Update missing movie posters where supported.
- **Local Storage** — Keep watchlist data in a local file.
- **Offline Watchlist Access** — Access locally saved watchlist data when online movie services are unavailable.

## 🛠️ Technologies Used

| Technology | Purpose |
|---|---|
| Java | Core application logic |
| Java Swing | Desktop graphical user interface |
| Gson | Processing JSON data from the API |
| TMDB API | Movie information and discovery |
| File handling | Local watchlist storage |
| Git and GitHub | Version control and project hosting |

## 📋 Requirements

- Java Development Kit (JDK) 21 or a compatible version.
- Windows, or another operating system with a compatible Java runtime.
- Internet connection for online movie discovery and search.
- A TMDB API Read Access Token for online features.

## 🚀 Download and Run

1. Open the [AbsoluteCinema Releases page](https://github.com/aaaaafz/AbsoluteCinema/releases).
2. Download the latest `AbsoluteCinema.zip` release asset.
3. Extract the ZIP file.
4. Open the extracted `AbsoluteCinema` folder.
5. Configure your TMDB token if you want online movie features.
6. Double-click **Run AbsoluteCinema.bat**.

Java must be installed and available on your system's PATH.

## 🔑 Configure TMDB

AbsoluteCinema uses TMDB to retrieve online movie information.

1. Open the extracted application folder.
2. Create a file named `tmdb.properties.local`.
3. Add the following line, replacing the placeholder with your own TMDB API Read Access Token:

```properties
TMDB_READ_ACCESS_TOKEN=YOUR_OWN_TMDB_TOKEN
```

4. Save the file and launch the application again.

**Security:** Never publish your real API token in GitHub source code, screenshots, or a public ZIP file. Keep `tmdb.properties.local` private and excluded from version control.

## 💻 Run from Source

Clone the repository:

```powershell
git clone https://github.com/aaaaafz/AbsoluteCinema.git
cd AbsoluteCinema
```

Compile the source files:

```powershell
javac -encoding UTF-8 -cp "lib\gson-2.13.2.jar" -d out src\model\Movie.java src\TmdbMovie.java src\TmdbClient.java src\MovieStorage.java src\AbsoluteCinemaGUI.java
```

Run the graphical application:

```powershell
java -cp "out;lib\gson-2.13.2.jar" AbsoluteCinemaGUI
```

These commands are intended for Windows PowerShell. Make sure the Gson JAR exists at `lib\gson-2.13.2.jar`.

## 📁 Project Structure

```text
AbsoluteCinema/
├── src/
│   ├── model/
│   │   └── Movie.java
│   ├── TmdbMovie.java
│   ├── TmdbClient.java
│   ├── MovieStorage.java
│   └── AbsoluteCinemaGUI.java
├── lib/
│   └── gson-2.13.2.jar
├── movies.txt
├── .gitignore
└── README.md
```

The downloadable release contains the compiled application, required Gson library, launcher, and supporting files. The exact contents may differ from the source structure shown above.

## 🧩 Main Modules

- **Movie Model:** Represents movie information and related properties.
- **TMDB Client:** Connects to TMDB and processes online movie data.
- **Movie Storage:** Handles locally stored watchlist information.
- **Graphical User Interface:** Provides the desktop screens and user interactions.
- **Movie Management:** Supports adding movies, tracking watched status, and personal ratings.


## 🔮 Future Enhancements

- A redesigned movie discovery interface.
- Advanced filtering and sorting.
- Movie recommendations based on personal preferences.
- Improved poster loading and caching.
- Search and filtering within the personal watchlist.
- Exporting watchlist data and generating statistics.


