<?php
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, PUT, DELETE");
header("Access-Control-Allow-Headers: Content-Type");

$host = "localhost";
$user = "root";
$pass = "";
$db   = "contactosgps";

$conn = new mysqli($host, $user, $pass, $db);

if ($conn->connect_error) {
    error_log("Error de conexión MySQL: " . $conn->connect_error);
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Error interno del servidor"]);
    exit;
}

$conn->set_charset("utf8mb4");
