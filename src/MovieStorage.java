
import model.Movie;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class MovieStorage {

    private static final Path FILE_PATH = Paths.get("movies.txt");

    public static ArrayList<Movie> loadMovies() {

        ArrayList<Movie> movies = new ArrayList<>();

        if (!Files.exists(FILE_PATH)) {
            return movies;
        }

        try (BufferedReader reader = Files.newBufferedReader(
                FILE_PATH, StandardCharsets.UTF_8)) {

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\|", -1);

                if (parts.length < 3) {
                    System.err.println("Skipping invalid movie record.");
                    continue;
                }

                try {
                    String title = parts[0];
                    int year = Integer.parseInt(parts[1]);
                    String genre = parts[2];

                    Movie movie = new Movie(title, year, genre);

                    if (parts.length >= 4 && !parts[3].isEmpty()) {
                        movie.setWatched(
                                Boolean.parseBoolean(parts[3]));
                    }

                    if (parts.length >= 5 && !parts[4].isEmpty()) {
                        movie.setRating(
                                Double.parseDouble(parts[4]));
                    }

                    if (parts.length >= 6 && !parts[5].isEmpty()) {
                        movie.setPosterPath(parts[5]);
                    }

                    // New field: liked status.
                    // Old records without this field default to false.
                    if (parts.length >= 7 && !parts[6].isEmpty()) {
                        movie.setLiked(
                                Boolean.parseBoolean(parts[6]));
                    }

                    movies.add(movie);

                } catch (NumberFormatException ex) {
                    System.err.println(
                            "Skipping a movie with invalid numeric data.");
                }
            }

        } catch (IOException ex) {
            System.err.println(
                    "Could not load movies: " + ex.getMessage());
        }

        return movies;
    }

    public static void saveMovies(ArrayList<Movie> movies) {

        try (BufferedWriter writer = Files.newBufferedWriter(
                FILE_PATH, StandardCharsets.UTF_8)) {

            for (Movie movie : movies) {

                writer.write(safeField(movie.getTitle()));
                writer.write("|");

                writer.write(String.valueOf(movie.getReleaseYear()));
                writer.write("|");

                writer.write(safeField(movie.getGenre()));
                writer.write("|");

                writer.write(String.valueOf(movie.isWatched()));
                writer.write("|");

                writer.write(String.valueOf(movie.getRating()));
                writer.write("|");

                writer.write(safeField(movie.getPosterPath()));
                writer.write("|");

                // Save liked status separately from watched status.
                writer.write(String.valueOf(movie.isLiked()));

                writer.newLine();
            }

            System.out.println("Movies saved successfully!");

        } catch (IOException ex) {
            System.err.println(
                    "Could not save movies: " + ex.getMessage());
        }
    }

    private static String safeField(String value) {

        if (value == null) {
            return "";
        }

        return value.replace("|", "/")
                    .replace("\n", " ")
                    .replace("\r", " ");
    }
}
