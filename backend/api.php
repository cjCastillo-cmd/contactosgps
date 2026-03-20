<?php
require_once "config.php";

$method = $_SERVER["REQUEST_METHOD"];
$request = isset($_GET["action"]) ? $_GET["action"] : "";

switch ($method) {
    case "GET":
        if ($request === "listar") {
            listar($conn);
        } elseif ($request === "buscar") {
            buscar($conn);
        } elseif ($request === "obtener") {
            obtener($conn);
        } else {
            respuesta(400, false, "Acción no válida");
        }
        break;

    case "POST":
        if ($request === "insertar") {
            insertar($conn);
        } elseif ($request === "actualizar") {
            actualizar($conn);
        } elseif ($request === "eliminar") {
            eliminar($conn);
        } else {
            respuesta(400, false, "Acción no válida");
        }
        break;

    default:
        respuesta(405, false, "Método no permitido");
        break;
}

$conn->close();

// ==================== FUNCIONES ====================

function listar($conn) {
    $stmt = $conn->prepare("SELECT * FROM contactos ORDER BY nombre ASC");
    $stmt->execute();
    $result = $stmt->get_result();
    $contactos = [];
    while ($row = $result->fetch_assoc()) {
        $row["foto"] = $row["foto"] ? obtenerUrlFoto($row["foto"]) : null;
        $contactos[] = $row;
    }
    $stmt->close();
    echo json_encode(["success" => true, "data" => $contactos]);
}

function buscar($conn) {
    $query = isset($_GET["q"]) ? "%" . $_GET["q"] . "%" : "%";
    $stmt = $conn->prepare("SELECT * FROM contactos WHERE nombre LIKE ? OR telefono LIKE ? ORDER BY nombre ASC");
    $stmt->bind_param("ss", $query, $query);
    $stmt->execute();
    $result = $stmt->get_result();
    $contactos = [];
    while ($row = $result->fetch_assoc()) {
        $row["foto"] = $row["foto"] ? obtenerUrlFoto($row["foto"]) : null;
        $contactos[] = $row;
    }
    $stmt->close();
    echo json_encode(["success" => true, "data" => $contactos]);
}

function obtener($conn) {
    $id = isset($_GET["id"]) ? intval($_GET["id"]) : 0;
    if ($id <= 0) {
        respuesta(400, false, "ID no válido");
        return;
    }
    $stmt = $conn->prepare("SELECT * FROM contactos WHERE id = ?");
    $stmt->bind_param("i", $id);
    $stmt->execute();
    $result = $stmt->get_result();
    if ($row = $result->fetch_assoc()) {
        $row["foto"] = $row["foto"] ? obtenerUrlFoto($row["foto"]) : null;
        echo json_encode(["success" => true, "data" => $row]);
    } else {
        respuesta(404, false, "Contacto no encontrado");
    }
    $stmt->close();
}

function insertar($conn) {
    $nombre = isset($_POST["nombre"]) ? trim($_POST["nombre"]) : "";
    $telefono = isset($_POST["telefono"]) ? trim($_POST["telefono"]) : "";
    $latitud = isset($_POST["latitud"]) ? floatval($_POST["latitud"]) : 0;
    $longitud = isset($_POST["longitud"]) ? floatval($_POST["longitud"]) : 0;

    if (empty($nombre) || empty($telefono)) {
        respuesta(400, false, "Nombre y teléfono son obligatorios");
        return;
    }

    if (strlen($nombre) > 255 || strlen($telefono) > 50) {
        respuesta(400, false, "Datos exceden longitud máxima");
        return;
    }

    $fotoNombre = null;
    if (isset($_FILES["foto"]) && $_FILES["foto"]["error"] === UPLOAD_ERR_OK) {
        $fotoNombre = subirFoto($_FILES["foto"]);
        if (!$fotoNombre) {
            respuesta(500, false, "Error al subir la foto");
            return;
        }
    }

    $stmt = $conn->prepare("INSERT INTO contactos (nombre, telefono, latitud, longitud, foto) VALUES (?, ?, ?, ?, ?)");
    $stmt->bind_param("ssdds", $nombre, $telefono, $latitud, $longitud, $fotoNombre);

    if ($stmt->execute()) {
        $id = $stmt->insert_id;
        $stmt->close();
        echo json_encode(["success" => true, "message" => "Contacto guardado", "id" => $id]);
    } else {
        error_log("Error SQL insertar: " . $conn->error);
        $stmt->close();
        respuesta(500, false, "Error interno del servidor");
    }
}

function actualizar($conn) {
    $id = isset($_POST["id"]) ? intval($_POST["id"]) : 0;
    $nombre = isset($_POST["nombre"]) ? trim($_POST["nombre"]) : "";
    $telefono = isset($_POST["telefono"]) ? trim($_POST["telefono"]) : "";
    $latitud = isset($_POST["latitud"]) ? floatval($_POST["latitud"]) : 0;
    $longitud = isset($_POST["longitud"]) ? floatval($_POST["longitud"]) : 0;

    if ($id <= 0 || empty($nombre) || empty($telefono)) {
        respuesta(400, false, "Datos incompletos");
        return;
    }

    if (strlen($nombre) > 255 || strlen($telefono) > 50) {
        respuesta(400, false, "Datos exceden longitud máxima");
        return;
    }

    // Si viene foto nueva, subirla
    if (isset($_FILES["foto"]) && $_FILES["foto"]["error"] === UPLOAD_ERR_OK) {
        $fotoNombre = subirFoto($_FILES["foto"]);
        if ($fotoNombre) {
            // Eliminar foto anterior
            eliminarFotoAnterior($conn, $id);
            $stmt = $conn->prepare("UPDATE contactos SET nombre=?, telefono=?, latitud=?, longitud=?, foto=? WHERE id=?");
            $stmt->bind_param("ssddsi", $nombre, $telefono, $latitud, $longitud, $fotoNombre, $id);
        } else {
            respuesta(500, false, "Error al subir la foto");
            return;
        }
    } else {
        $stmt = $conn->prepare("UPDATE contactos SET nombre=?, telefono=?, latitud=?, longitud=? WHERE id=?");
        $stmt->bind_param("ssddi", $nombre, $telefono, $latitud, $longitud, $id);
    }

    if ($stmt->execute()) {
        $stmt->close();
        echo json_encode(["success" => true, "message" => "Contacto actualizado"]);
    } else {
        error_log("Error SQL actualizar: " . $conn->error);
        $stmt->close();
        respuesta(500, false, "Error interno del servidor");
    }
}

function eliminar($conn) {
    $id = isset($_POST["id"]) ? intval($_POST["id"]) : 0;
    if ($id <= 0) {
        respuesta(400, false, "ID no válido");
        return;
    }

    // Eliminar foto del servidor
    eliminarFotoAnterior($conn, $id);

    $stmt = $conn->prepare("DELETE FROM contactos WHERE id = ?");
    $stmt->bind_param("i", $id);

    if ($stmt->execute()) {
        $stmt->close();
        echo json_encode(["success" => true, "message" => "Contacto eliminado"]);
    } else {
        error_log("Error SQL eliminar: " . $conn->error);
        $stmt->close();
        respuesta(500, false, "Error interno del servidor");
    }
}

// ==================== UTILIDADES ====================

function subirFoto($archivo) {
    // Validar tamaño (max 5MB)
    if ($archivo["size"] > 5 * 1024 * 1024) {
        return null;
    }

    $extension = strtolower(pathinfo($archivo["name"], PATHINFO_EXTENSION));
    $permitidas = ["jpg", "jpeg", "png", "gif"];
    if (!in_array($extension, $permitidas)) {
        return null;
    }

    // Validar MIME type real del archivo
    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $mimeReal = $finfo->file($archivo["tmp_name"]);
    $mimePermitidos = ["image/jpeg", "image/png", "image/gif"];
    if (!in_array($mimeReal, $mimePermitidos)) {
        return null;
    }

    $nombreArchivo = "contacto_" . bin2hex(random_bytes(16)) . "." . $extension;
    $destino = __DIR__ . "/uploads/" . $nombreArchivo;
    if (move_uploaded_file($archivo["tmp_name"], $destino)) {
        return $nombreArchivo;
    }
    return null;
}

function eliminarFotoAnterior($conn, $id) {
    $stmt = $conn->prepare("SELECT foto FROM contactos WHERE id = ?");
    $stmt->bind_param("i", $id);
    $stmt->execute();
    $result = $stmt->get_result();
    if ($row = $result->fetch_assoc()) {
        if ($row["foto"]) {
            $ruta = __DIR__ . "/uploads/" . $row["foto"];
            if (file_exists($ruta)) {
                unlink($ruta);
            }
        }
    }
    $stmt->close();
}

function obtenerUrlFoto($nombreArchivo) {
    $protocolo = isset($_SERVER["HTTPS"]) && $_SERVER["HTTPS"] === "on" ? "https" : "http";
    $host = $_SERVER["HTTP_HOST"];
    return $protocolo . "://" . $host . "/contactosgps/uploads/" . $nombreArchivo;
}

function respuesta($code, $success, $message) {
    http_response_code($code);
    echo json_encode(["success" => $success, "message" => $message]);
}
