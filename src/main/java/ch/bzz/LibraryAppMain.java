package ch.bzz;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.function.Consumer;

public class LibraryAppMain {

    private static final Book BOOK_1 = new Book(
            1,
            "978-3-8362-9544-4",
            "Java ist auch eine Insel",
            "Christian Ullenboom",
            2023
    );
    private static final Book BOOK_2 = new Book(
            2,
            "978-3-658-43573-8",
            "Grundkurs Java",
            "Dietmar Abts",
            2024
    );

    private static final Map<String, Consumer<String>> COMMANDS = new LinkedHashMap<>();

    static {
        COMMANDS.put("help", argument -> printHelp());
        COMMANDS.put("listBooks", argument -> listBooks());
        COMMANDS.put("importBooks", LibraryAppMain::importBooks);
        COMMANDS.put("quit", argument -> { });
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                String input = scanner.nextLine().trim();
                if (input.equals("quit")) {
                    break;
                }
                String[] parts = input.split("\\s+", 2);
                String commandName = parts[0];
                String argument = parts.length > 1 ? parts[1] : "";
                Consumer<String> command = COMMANDS.get(commandName);
                if (command != null) {
                    command.accept(argument);
                } else {
                    System.out.println("Unknown command: " + input);
                }
            }
        }
    }

    private static void printHelp() {
        System.out.println("Available commands:");
        for (String name : COMMANDS.keySet()) {
            System.out.println("  " + name);
        }
    }

    private static void listBooks() {
        for (Book book : getBooksFromDatabase()) {
            System.out.println(book.getTitle());
        }
    }

    private static void importBooks(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            System.out.println("Usage: importBooks <FILE_PATH>");
            return;
        }
        List<Book> books = readBooksFromTsv(Path.of(filePath));
        saveBooksToDatabase(books);
    }

    private static List<Book> readBooksFromTsv(Path filePath) {
        List<Book> books = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                return books;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                if (columns.length < 5) {
                    continue;
                }
                books.add(new Book(
                        Integer.parseInt(columns[0].trim()),
                        columns[1].trim(),
                        columns[2].trim(),
                        columns[3].trim(),
                        Integer.parseInt(columns[4].trim())
                ));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read books from " + filePath, e);
        }
        return books;
    }

    private static void saveBooksToDatabase(List<Book> books) {
        Properties config = loadConfig();
        String url = config.getProperty("DB_URL");
        String user = config.getProperty("DB_USER");
        String password = config.getProperty("DB_PASSWORD");

        String sql = """
                INSERT INTO books (id, isbn, title, author, publication_year)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    isbn = EXCLUDED.isbn,
                    title = EXCLUDED.title,
                    author = EXCLUDED.author,
                    publication_year = EXCLUDED.publication_year
                """;

        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Book book : books) {
                statement.setInt(1, book.getId());
                statement.setString(2, book.getIsbn());
                statement.setString(3, book.getTitle());
                statement.setString(4, book.getAuthor());
                statement.setInt(5, book.getYear());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save books to database", e);
        }
    }

    private static List<Book> getBooksFromDatabase() {
        List<Book> books = new ArrayList<>();
        Properties config = loadConfig();
        String url = config.getProperty("DB_URL");
        String user = config.getProperty("DB_USER");
        String password = config.getProperty("DB_PASSWORD");

        String sql = "SELECT id, isbn, title, author, publication_year FROM books ORDER BY id";
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                books.add(new Book(
                        resultSet.getInt("id"),
                        resultSet.getString("isbn"),
                        resultSet.getString("title"),
                        resultSet.getString("author"),
                        resultSet.getInt("publication_year")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load books from database", e);
        }
        return books;
    }

    private static Properties loadConfig() {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream("config.properties")) {
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
        return properties;
    }
}
