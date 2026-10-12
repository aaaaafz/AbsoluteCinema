
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import com.google.gson.Gson;

public class TmdbClient {

    private static final String BASE_URL =
            "https://api.themoviedb.org/3";

    private static final HttpClient CLIENT =
            HttpClient.newHttpClient();

    private static final Gson gson = new Gson();

    
private static String getToken() {
    String token = System.getenv("TMDB_READ_ACCESS_TOKEN");

    if (token != null && !token.isBlank()) {
        return token.trim();
    }

    java.util.Properties properties = new java.util.Properties();

    String[] files = {
        "tmdb.properties.local",
        "tmdb.properties"
    };

    for (String file : files) {
        try (java.io.InputStream input =
                java.nio.file.Files.newInputStream(
                    java.nio.file.Path.of(file))) {

            properties.clear();
            properties.load(input);

            token = properties.getProperty("TMDB_READ_ACCESS_TOKEN");

            if (token != null && !token.isBlank()
                    && !token.equals("PASTE_YOUR_OWN_TMDB_TOKEN_HERE")) {
                return token.trim();
            }

        } catch (java.io.IOException ignored) {
        }
    }

    throw new IllegalStateException(
        "TMDB token is missing. Configure tmdb.properties.local "
        + "or the TMDB_READ_ACCESS_TOKEN environment variable."
    );
}


    private static String getJson(String endpoint)
            throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + getToken())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "TMDB request failed: HTTP "
                            + response.statusCode()
            );
        }

        return response.body();
    }

    // Search for movies by title
    public static String searchMovies(String query)
            throws Exception {

        String encodedQuery = URLEncoder.encode(
                query, StandardCharsets.UTF_8
        );

        return getJson(
                "/search/movie?query=" + encodedQuery
        );
    }

    // Movies trending this week
    public static String getTrendingMovies()
            throws Exception {

        return getJson("/trending/movie/week");
    }

    // Popular movies
    public static String getPopularMovies()
            throws Exception {

        return getJson("/movie/popular");
    }

    // Highest-rated movies
    public static String getTopRatedMovies()
            throws Exception {

        return getJson("/movie/top_rated");
    }

    // Upcoming releases
    public static String getUpcomingMovies()
            throws Exception {

        return getJson("/movie/upcoming");
    }

    // Movies currently in theatres
    public static String getNowPlayingMovies()
            throws Exception {

        return getJson("/movie/now_playing");
    }

    
    // Get full details for a movie, including cast, trailers and recommendations
    public static String getMovieDetails(int movieId)
            throws Exception {

        if (movieId <= 0) {
            throw new IllegalArgumentException(
                    "Movie ID must be greater than zero."
            );
        }

        return getJson(
                "/movie/" + movieId
                        + "?append_to_response=videos,credits,similar,recommendations"
        );
    }


    // Discover movies belonging to a genre
    public static String discoverMoviesByGenre(int genreId)
            throws Exception {

        return getJson(
                "/discover/movie?with_genres=" + genreId
                        + "&sort_by=popularity.desc"
        );
    }

    // Get the available movie genres
    public static Map<Integer, String> getMovieGenres()
            throws Exception {

        String json = getJson("/genre/movie/list");

        GenreResponse result = gson.fromJson(
                json, GenreResponse.class
        );

        Map<Integer, String> genres = new HashMap<>();

        if (result.genres != null) {
            for (Genre genre : result.genres) {
                genres.put(genre.id, genre.name);
            }
        }

        return genres;
    }

    // Simple console test
    public static void main(String[] args) {

        try (Scanner sc = new Scanner(System.in)) {

            System.out.println("===== ABSOLUTECINEMA TMDB TEST =====");
            System.out.println("1. Search Movies");
            System.out.println("2. Trending Movies");
            System.out.println("3. Popular Movies");
            System.out.println("4. Top Rated Movies");
            System.out.println("5. Upcoming Movies");
            System.out.println("6. Movies in Theatres");

            System.out.print("Choose an option: ");

            String choice = sc.nextLine().trim();
            String json;

            switch (choice) {
                case "1":
                    System.out.print("Enter a movie title: ");
                    String query = sc.nextLine().trim();

                    if (query.isEmpty()) {
                        System.out.println(
                                "Please enter a movie title."
                        );
                        return;
                    }

                    json = searchMovies(query);
                    break;

                case "2":
                    json = getTrendingMovies();
                    break;

                case "3":
                    json = getPopularMovies();
                    break;

                case "4":
                    json = getTopRatedMovies();
                    break;

                case "5":
                    json = getUpcomingMovies();
                    break;

                case "6":
                    json = getNowPlayingMovies();
                    break;

                default:
                    System.out.println("Invalid option.");
                    return;
            }

            TmdbResponse result = gson.fromJson(
                    json, TmdbResponse.class
            );

            if (result.results == null
                    || result.results.length == 0) {

                System.out.println("No movies found.");
                return;
            }

            System.out.println("\n===== MOVIES =====\n");

            for (TmdbMovie movie : result.results) {

                String year = "Unknown";

                if (movie.getRelease_date() != null
                        && movie.getRelease_date().length() >= 4) {

                    year = movie.getRelease_date()
                            .substring(0, 4);
                }

                System.out.println(
                        "Title: " + movie.getTitle()
                );

                System.out.println("Year: " + year);

                System.out.printf(
                        "TMDB Rating: %.1f/10%n",
                        movie.getVote_average()
                );

                System.out.println("-------------------------");
            }

        } catch (Exception e) {
            System.out.println(
                    "TMDB error: " + e.getMessage()
            );
        }
    }

    private static class TmdbResponse {
        TmdbMovie[] results;
    }

    private static class GenreResponse {
        Genre[] genres;
    }

    private static class Genre {
        int id;
        String name;
    }
}
