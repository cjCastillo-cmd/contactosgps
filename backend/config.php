<?php
/**
 * Configuracion de la base de datos y cabeceras HTTP.
 * Este archivo se incluye en api.php para establecer la conexion.
 */

// Cabeceras para respuestas JSON y CORS (permite peticiones desde cualquier origen)
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, PUT, DELETE");
header("Access-Control-Allow-Headers: Content-Type");

// Credenciales de la base de datos MySQL (XAMPP por defecto)
$host = "localhost";
$user = "root";
$pass = "";     // XAMPP no tiene password por defecto
$db   = "contactosgps";

// Crear conexion a MySQL
$conn = new mysqli($host, $user, $pass, $db);

// Verificar que la conexion fue exitosa
if ($conn->connect_error) {
    error_log("Error de conexion MySQL: " . $conn->connect_error);
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Error interno del servidor"]);
    exit;
}

// Usar codificacion UTF-8 para soportar caracteres especiales
$conn->set_charset("utf8mb4");