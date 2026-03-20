<?php
/**
 * API REST para gestionar contactos con ubicacion GPS.
 * Endpoints disponibles:
 *   GET  ?action=listar    -> Lista todos los contactos
 *   GET  ?action=buscar    -> Busca contactos por nombre o telefono
 *   GET  ?action=obtener   -> Obtiene un contacto por ID
 *   POST ?action=insertar  -> Crea un nuevo contacto
 *   POST ?action=actualizar-> Actualiza un contacto existente
 *   POST ?action=eliminar  -> Elimina un contacto por ID
 */
require_once "config.php";

// Leer el metodo HTTP y la accion solicitada
$method = $_SERVER["REQUEST_METHOD"];
$request = isset($_GET["action"]) ? $_GET["action"] : "";

// Enrutar la peticion segun el metodo HTTP y la accion
switch ($method) {
    case "GET":
        if ($request === "listar") {
            listar($conn);
        } elseif ($request === "buscar") {
            buscar($conn);
        } elseif ($request === "obtener") {
            obtener($conn);
        } else {
            respuesta(400, false, "Accion no valida");
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
            respuesta(400, false, "Accion no valida");
        }
        break;

    default:
        respuesta(405, false, "Metodo no permitido");
        break;
}

$conn->close();

// ==================== FUNCIONES CRUD ====================

/** Lista todos los contactos ordenados por nombre */
function listar($conn) {
    $stmt = $conn->prepare("SELECT id, nombre, telefono, latitud, longitud, foto FROM contactos ORDER BY nombre ASC");
    $stmt->execute();
    $result = $stmt->get_result();

    $contactos = [];
    while ($row = $result->fetch_assoc()) {
        // Convertir nombre de archivo a URL completa
        $row["foto"] = $row["foto"] ? obtenerUrlFoto($row["foto"]) : null;
        $contactos[] = $row;
    }

    $stmt->close();
    echo json_encode(["success" => true, "data" => $contactos]);
}

/** Busca contactos por nombre o telefono (parametro ?q=texto) */
function buscar($conn) {
    $query = isset($_GET["q"]) ? "%" . $_GET["q"] . "%" : "%";
    $stmt = $conn->prepare("SELECT id, nombre, telefono, latitud, longitud, foto FROM contactos WHERE nombre LIKE ? OR telefono LIKE ? ORDER BY nombre ASC");
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

/** Obtiene un contacto por su ID */
function obtener($conn) {
    $id = isset($_GET["id"]) ? intval($_GET["id"]) : 0;
    if ($id <= 0) {
        respuesta(400, false, "ID no valido");
        return;
    }

    $stmt = $conn->prepare("SELECT id, nombre, telefono, latitud, longitud, foto FROM contactos WHERE id = ?");
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

/** Crea un nuevo contacto (recibe datos por POST + foto opcional) */
function insertar($conn) {
    // Leer y limpiar datos del formulario
    $nombre = isset($_POST["nombre"]) ? trim($_POST["nombre"]) : "";
    $telefono = isset($_POST["telefono"]) ? trim($_POST["telefono"]) : "";
    $latitud = isset($_POST["latitud"]) ? floatval($_POST["latitud"]) : 0;
    $longitud = isset($_POST["longitud"]) ? floatval($_POST["longitud"]) : 0;

    // Validaciones
    if (empty($nombre) || empty($telefono)) {
        respuesta(400, false, "Nombre y telefono son obligatorios");
        return;
    }
    if (strlen($nombre) > 255 || strlen($telefono) > 50) {
        respuesta(400, false, "Datos exceden longitud maxima");
        return;
    }

    // Subir foto si viene en la peticion
    $fotoNombre = null;
    if (isset($_FILES["foto"]) && $_FILES["foto"]["error"] === UPLOAD_ERR_OK) {
        $fotoNombre = subirFoto($_FILES["foto"]);
        if (!$fotoNombre) {
            respuesta(500, false, "Error al subir la foto");
            return;
        }
    }

    // Insertar en la base de datos
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

/** Actualiza un contacto existente (recibe datos por POST + foto opcional) */
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
        respuesta(400, false, "Datos exceden longitud maxima");
        return;
    }

    // Si viene foto nueva, subirla y eliminar la anterior
    if (isset($_FILES["foto"]) && $_FILES["foto"]["error"] === UPLOAD_ERR_OK) {
        $fotoNombre = subirFoto($_FILES["foto"]);
        if ($fotoNombre) {
            eliminarFotoAnterior($conn, $id);
            $stmt = $conn->prepare("UPDATE contactos SET nombre=?, telefono=?, latitud=?, longitud=?, foto=? WHERE id=?");
            $stmt->bind_param("ssddsi", $nombre, $telefono, $latitud, $longitud, $fotoNombre, $id);
        } else {
            respuesta(500, false, "Error al subir la foto");
            return;
        }
    } else {
        // Sin foto nueva: solo actualizar datos de texto
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

/** Elimina un contacto por ID (tambien borra su foto del servidor) */
function eliminar($conn) {
    $id = isset($_POST["id"]) ? intval($_POST["id"]) : 0;
    if ($id <= 0) {
        respuesta(400, false, "ID no valido");
        return;
    }

    // Primero eliminar la foto del disco
    eliminarFotoAnterior($conn, $id);

    // Luego eliminar el registro de la base de datos
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

// ==================== FUNCIONES AUXILIARES ====================

/**
 * Sube una foto al servidor con validaciones de seguridad.
 * Retorna el nombre del archivo guardado o null si falla.
 */
function subirFoto($archivo) {
    // Validar tamano (maximo 5MB)
    if ($archivo["size"] > 5 * 1024 * 1024) {
        return null;
    }

    // Validar extension del archivo
    $extension = strtolower(pathinfo($archivo["name"], PATHINFO_EXTENSION));
    $permitidas = ["jpg", "jpeg", "png", "gif"];
    if (!in_array($extension, $permitidas)) {
        return null;
    }

    // Validar que el contenido real sea una imagen (no solo la extension)
    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $mimeReal = $finfo->file($archivo["tmp_name"]);
    $mimePermitidos = ["image/jpeg", "image/png", "image/gif"];
    if (!in_array($mimeReal, $mimePermitidos)) {
        return null;
    }

    // Generar nombre unico para evitar colisiones
    $nombreArchivo = "contacto_" . bin2hex(random_bytes(16)) . "." . $extension;
    $destino = __DIR__ . "/uploads/" . $nombreArchivo;

    if (move_uploaded_file($archivo["tmp_name"], $destino)) {
        return $nombreArchivo;
    }
    return null;
}

/** Elimina la foto anterior de un contacto del disco */
function eliminarFotoAnterior($conn, $id) {
    $stmt = $conn->prepare("SELECT foto FROM contactos WHERE id = ?");
    $stmt->bind_param("i", $id);
    $stmt->execute();
    $result = $stmt->get_result();

    if ($row = $result->fetch_assoc()) {
        if ($row["foto"]) {
            $ruta = __DIR__ . "/uploads/" . $row["foto"];
            if (file_exists($ruta)) {
                unlink($ruta); // Borrar archivo del disco
            }
        }
    }

    $stmt->close();
}

/** Construye la URL publica de una foto a partir de su nombre de archivo */
function obtenerUrlFoto($nombreArchivo) {
    $protocolo = isset($_SERVER["HTTPS"]) && $_SERVER["HTTPS"] === "on" ? "https" : "http";
    $host = $_SERVER["HTTP_HOST"];
    return $protocolo . "://" . $host . "/contactosgps/uploads/" . $nombreArchivo;
}

/** Envia una respuesta JSON con codigo HTTP, estado y mensaje */
function respuesta($code, $success, $message) {
    http_response_code($code);
    echo json_encode(["success" => $success, "message" => $message]);
}