
import model.Movie;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;
import com.google.gson.Gson;

public class Main {

    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        ArrayList<Movie> watchlist = MovieStorage.loadMovies();

        int choice = 0;

        do {
            System.out.println("\n===== ABSOLUTECINEMA =====");
            System.out.println("1. View Watchlist");
            System.out.println("2. Add Movie Manually");
            System.out.println("3. Mark Movie as Watched");
            System.out.println("4. Rate a Movie");
            System.out.println("5. Exit");
            System.out.println("6. Search Movies on TMDB");
            System.out.println("7. Update Missing Posters");
            System.out.print("Enter your choice: ");

            if (!sc.hasNextInt()) {
                System.out.println("Please enter a number.");
                sc.nextLine();
                continue;
            }

            choice = sc.nextInt();
            sc.nextLine();

            switch (choice) {
                case 1:
                    System.out.println("\n--- MY WATCHLIST ---");
                    for (int i = 0; i < watchlist.size(); i++) {
                        Movie m = watchlist.get(i);
                        System.out.println(
                            (i + 1) + ". " + m.getTitle()
                            + " (" + m.getReleaseYear() + ")"
                            + " - " + m.getGenre()
                            + " | Watched: " + m.isWatched()
                            + " | My Rating: " + m.getRating() + "/10"
                        );
                    }
                    System.out.println("Total movies: " + watchlist.size());
                    break;

                case 2:
                    System.out.print("Enter movie title: ");
                    String title = sc.nextLine().trim();

                    if (title.isEmpty()) {
                        System.out.println("Movie title cannot be empty.");
                        break;
                    }

                    System.out.print("Enter release year: ");
                    int year;

                    try {
                        year = Integer.parseInt(sc.nextLine().trim());
                        if (year < 0) {
                            System.out.println("Year cannot be negative.");
                            break;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid year.");
                        break;
                    }

                    System.out.print("Enter genre: ");
                    String genre = sc.nextLine().trim();

                    if (genre.isEmpty()) {
                        genre = "Unknown";
                    }

                    watchlist.add(new Movie(title, year, genre));
                    MovieStorage.saveMovies(watchlist);
                    System.out.println("Movie added successfully!");
                    break;

                case 3:
                    if (watchlist.isEmpty()) {
                        System.out.println("Your watchlist is empty.");
                        break;
                    }

                    showMovies(watchlist);
                    System.out.print("Enter movie number: ");

                    if (!sc.hasNextInt()) {
                        System.out.println("Please enter a valid number.");
                        sc.nextLine();
                        break;
                    }

                    int watchedChoice = sc.nextInt();
                    sc.nextLine();

                    if (watchedChoice >= 1
                            && watchedChoice <= watchlist.size()) {
                        Movie m = watchlist.get(watchedChoice - 1);
                        m.setWatched(true);
                        MovieStorage.saveMovies(watchlist);
                        System.out.println(m.getTitle() + " marked as watched!");
                    } else {
                        System.out.println("Invalid movie number.");
                    }
                    break;

                case 4:
                    if (watchlist.isEmpty()) {
                        System.out.println("Your watchlist is empty.");
                        break;
                    }

                    showMovies(watchlist);
                    System.out.print("Enter movie number: ");

                    if (!sc.hasNextInt()) {
                        System.out.println("Please enter a valid number.");
                        sc.nextLine();
                        break;
                    }

                    int ratingChoice = sc.nextInt();
                    sc.nextLine();

                    if (ratingChoice < 1 || ratingChoice > watchlist.size()) {
                        System.out.println("Invalid movie number.");
                        break;
                    }

                    System.out.print("Enter your rating (0-10): ");

                    if (!sc.hasNextDouble()) {
                        System.out.println("Please enter a valid rating.");
                        sc.nextLine();
                        break;
                    }

                    double rating = sc.nextDouble();
                    sc.nextLine();

                    if (rating >= 0 && rating <= 10) {
                        Movie m = watchlist.get(ratingChoice - 1);
                        m.setRating(rating);
                        MovieStorage.saveMovies(watchlist);
                        System.out.println("Rating saved for " + m.getTitle() + "!");
                    } else {
                        System.out.println("Enter a rating from 0 to 10.");
                    }
                    break;

                case 5:
                    MovieStorage.saveMovies(watchlist);
                    System.out.println("Thank you for using AbsoluteCinema!");
                    break;

                case 6:
                    searchAndAddMovie(sc, watchlist);
                    break;

                case 7:
                    updateMissingPosters(watchlist);
                    break;

                default:
                    System.out.println("Invalid choice. Please try again.");
            }

        } while (choice != 5);

        sc.close();
    }

    private static void showMovies(ArrayList<Movie> watchlist) {
        for (int i = 0; i < watchlist.size(); i++) {
            System.out.println(
                (i + 1) + ". " + watchlist.get(i).getTitle()
                + " (" + watchlist.get(i).getReleaseYear() + ")"
            );
        }
    }

    private static void updateMissingPosters(ArrayList<Movie> watchlist) {
        int updated = 0;
        int failed = 0;

        System.out.println("\n--- UPDATE MISSING POSTERS ---");

        for (Movie movie : watchlist) {
            String currentPath = movie.getPosterPath();

            if (currentPath != null && !currentPath.isBlank()) {
                continue;
            }

            System.out.println("\nSearching for: " + movie.getTitle());

            try {
                String json = TmdbClient.searchMovies(movie.getTitle());
                TmdbResponse response = gson.fromJson(json, TmdbResponse.class);

                if (response == null || response.results == null
                        || response.results.length == 0) {
                    System.out.println("No TMDB results found.");
                    failed++;
                    continue;
                }

                TmdbMovie match = null;

                // Prefer a title and release-year match.
                for (TmdbMovie result : response.results) {
                    if (result.getTitle() == null
                            || result.getPoster_path() == null
                            || result.getPoster_path().isBlank()) {
                        continue;
                    }

                    if (result.getTitle().equalsIgnoreCase(movie.getTitle())
                            && getYear(result) == movie.getReleaseYear()) {
                        match = result;
                        break;
                    }
                }

                // If the year cannot be matched, accept only an exact title.
                if (match == null) {
                    for (TmdbMovie result : response.results) {
                        if (result.getTitle() != null
                                && result.getPoster_path() != null
                                && !result.getPoster_path().isBlank()
                                && result.getTitle().equalsIgnoreCase(
                                    movie.getTitle())) {
                            match = result;
                            break;
                        }
                    }
                }

                if (match == null) {
                    System.out.println("No suitable poster match found.");
                    failed++;
                    continue;
                }

                movie.setPosterPath(match.getPoster_path());
                MovieStorage.saveMovies(watchlist);
                updated++;

                System.out.println(
                    "Poster saved: " + match.getTitle()
                    + " (" + getYear(match) + ")"
                );

            } catch (Exception e) {
                System.out.println("Search failed: " + e.getMessage());
                failed++;
            }
        }

        MovieStorage.saveMovies(watchlist);

        System.out.println("\n--- POSTER UPDATE COMPLETE ---");
        System.out.println("Posters updated: " + updated);
        System.out.println("Movies needing attention: " + failed);
        System.out.println("Your watched status and ratings were preserved.");
    }

    private static void searchAndAddMovie(
            Scanner sc, ArrayList<Movie> watchlist) {
        try {
            System.out.print("\nEnter movie title to search: ");
            String query = sc.nextLine().trim();

            if (query.isEmpty()) {
                System.out.println("Please enter a movie title.");
                return;
            }

            String json = TmdbClient.searchMovies(query);
            TmdbResponse response = gson.fromJson(json, TmdbResponse.class);

            if (response == null || response.results == null
                    || response.results.length == 0) {
                System.out.println("No movies found.");
                return;
            }

            Map<Integer, String> genres = TmdbClient.getMovieGenres();

            System.out.println("\n--- TMDB SEARCH RESULTS ---");

            for (int i = 0; i < response.results.length; i++) {
                TmdbMovie m = response.results[i];
                System.out.printf(
                    "%d. %s (%s) | Genres: %s | TMDB Rating: %.1f/10%n",
                    i + 1,
                    m.getTitle(),
                    getYear(m) == 0 ? "Unknown" : String.valueOf(getYear(m)),
                    getGenreNames(m.getGenre_ids(), genres),
                    m.getVote_average()
                );
            }

            System.out.println("0. Cancel");
            System.out.print("Enter the number of the movie to view: ");

            if (!sc.hasNextInt()) {
                System.out.println("Please enter a valid number.");
                sc.nextLine();
                return;
            }

            int selected = sc.nextInt();
            sc.nextLine();

            if (selected == 0) {
                return;
            }

            if (selected < 1 || selected > response.results.length) {
                System.out.println("Invalid movie number.");
                return;
            }

            TmdbMovie chosen = response.results[selected - 1];
            int releaseYear = getYear(chosen);
            String genre = getGenreNames(chosen.getGenre_ids(), genres);

            System.out.println("\n===== MOVIE DETAILS =====");
            System.out.println("Title: " + chosen.getTitle());
            System.out.println("Release year: "
                    + (releaseYear == 0 ? "Unknown" : releaseYear));
            System.out.println("Genres: " + genre);
            System.out.printf("TMDB Rating: %.1f/10%n",
                    chosen.getVote_average());
            System.out.println("\nOverview:");
            System.out.println(
                chosen.getOverview() == null
                    || chosen.getOverview().isBlank()
                    ? "No overview available."
                    : chosen.getOverview()
            );

            for (Movie existing : watchlist) {
                if (existing.getTitle().equalsIgnoreCase(chosen.getTitle())
                        && existing.getReleaseYear() == releaseYear) {
                    System.out.println("This movie is already in your watchlist.");
                    return;
                }
            }

            System.out.print("\nAdd this movie to AbsoluteCinema? (y/n): ");

            if (!sc.nextLine().trim().equalsIgnoreCase("y")) {
                System.out.println("Movie not added.");
                return;
            }

            Movie newMovie = new Movie(chosen.getTitle(), releaseYear, genre);
            newMovie.setPosterPath(chosen.getPoster_path());
            watchlist.add(newMovie);
            MovieStorage.saveMovies(watchlist);

            System.out.println(chosen.getTitle()
                    + " added to your AbsoluteCinema watchlist!");

        } catch (Exception e) {
            System.out.println("Movie search failed: " + e.getMessage());
        }
    }

    private static String getGenreNames(
            int[] genreIds, Map<Integer, String> genres) {
        if (genreIds == null || genreIds.length == 0) {
            return "Unknown";
        }

        ArrayList<String> names = new ArrayList<>();

        for (int id : genreIds) {
            String name = genres.get(id);
            if (name != null) {
                names.add(name);
            }
        }

        return names.isEmpty() ? "Unknown" : String.join(", ", names);
    }

    private static int getYear(TmdbMovie movie) {
        String date = movie.getRelease_date();

        if (date != null && date.length() >= 4) {
            try {
                return Integer.parseInt(date.substring(0, 4));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        return 0;
    }

    private static class TmdbResponse {
        TmdbMovie[] results;
    }
}
