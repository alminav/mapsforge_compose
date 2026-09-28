<?php
header('Content-Type: application/json; charset=utf-8');

// 1. Datenbank-Zugangsdaten von bplaced einfügen
$host     = 'localhost'; 
$dbname   = 'almica_db';
$username = 'almica_db';
$password = 'Edc4#rfv';

// Nur POST-Anfragen erlauben
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(["status" => "error", "message" => "Nur POST-Anfragen sind erlaubt."]);
    exit;
}

try {
    $pdo = new PDO("mysql:host=$host;dbname=$dbname;charset=utf8mb4", $username, $password, [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);

    // 2. Alle Datensätze löschen und ID-Zähler zurücksetzen
    $sql = "TRUNCATE TABLE locations";
    $pdo->exec($sql);

    // Erfolgsantwort senden
    echo json_encode([
        "status" => "success",
        "message" => "Alle Standorte wurden erfolgreich gelöscht."
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        "status" => "error",
        "message" => "Datenbankfehler: " . $e->getMessage()
    ]);
}
?>
