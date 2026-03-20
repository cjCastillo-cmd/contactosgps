===============================================
 CONTACTOS GPS - Guia de instalacion y prueba
===============================================

Proyecto: App Android (Kotlin) + REST API (PHP/MySQL)
Permite crear, editar, eliminar contactos con foto y ubicacion GPS,
verlos en un mapa interactivo y realizar llamadas.


REQUISITOS
----------
- Android Studio (ultima version)
- XAMPP instalado (Apache + MySQL)
- Emulador Android o celular fisico con Android 7+


PASO 1: CONFIGURAR EL BACKEND (REST API)
-----------------------------------------
1. Copiar la carpeta "backend" del repo dentro de C:\xampp\htdocs\
   y renombrarla a "contactosgps":

   C:\xampp\htdocs\contactosgps\
       api.php
       config.php
       database.sql

2. Crear la carpeta "uploads" dentro:
   C:\xampp\htdocs\contactosgps\uploads\

3. Abrir XAMPP Control Panel y encender Apache y MySQL.

4. Crear la base de datos:
   - Abrir el navegador en http://localhost/phpmyadmin
   - Ir a la pestana SQL
   - Copiar y pegar el contenido de database.sql y ejecutar

   O desde terminal:
   C:\xampp\mysql\bin\mysql.exe -u root < C:\xampp\htdocs\contactosgps\database.sql

5. Verificar que funcione abriendo en el navegador:
   http://localhost/contactosgps/api.php?action=listar

   Debe mostrar: {"success":true,"data":[]}


PASO 2: CONFIGURAR LA APP ANDROID
----------------------------------
1. Abrir el proyecto en Android Studio.
2. Esperar a que Gradle sincronice las dependencias.
3. Ejecutar la app en un emulador o dispositivo fisico.

IMPORTANTE - Segun tu dispositivo de prueba:

  * EMULADOR: No cambiar nada. La IP 10.0.2.2 ya apunta
    al localhost de tu PC automaticamente.

  * CELULAR FISICO: Abrir el archivo
    app/src/main/java/com/example/contactosgps/ApiService.kt
    y cambiar la IP 10.0.2.2 por la IP real de tu PC.

    Para saber tu IP: abrir CMD y escribir "ipconfig",
    buscar "Direccion IPv4" (ejemplo: 192.168.1.100).

    El celular y la PC deben estar en la MISMA red WiFi.


PASO 3: UBICACION GPS EN EL EMULADOR
--------------------------------------
El emulador no tiene GPS real. Para simular una ubicacion:

1. En el emulador, hacer click en los 3 puntos (...) del panel lateral.
2. Ir a "Location".
3. Escribir una direccion o poner coordenadas manualmente.
4. Click en "Set Location".

Despues de eso la app tomara esas coordenadas.


COMO USAR LA APP
-----------------
- Pantalla principal: crear contacto (tomar foto, llenar datos, GPS automatico)
- Boton "Ver Contactos": lista todos los contactos guardados
- Click en un contacto: menu con opciones Ver en mapa, Editar, Eliminar
- Deslizar a la derecha: editar contacto
- Deslizar a la izquierda: eliminar contacto
- Icono de mapa: ver ubicacion del contacto en el mapa
- En el mapa: boton para centrar y boton para llamar


PROBLEMAS COMUNES
------------------
1. "Error de conexion" al abrir la app
   -> XAMPP apagado o base de datos no creada.
   -> Encender Apache + MySQL e importar database.sql.

2. No carga fotos de contactos
   -> La carpeta "uploads" no existe.
   -> Crearla en C:\xampp\htdocs\contactosgps\uploads\

3. "Error de conexion" en celular fisico
   -> La IP en ApiService.kt es incorrecta.
   -> Poner la IP real de la PC (ipconfig).
   -> Verificar que esten en la misma red WiFi.

4. El puerto 80 esta ocupado
   -> Skype, IIS u otro programa usa el puerto.
   -> Cerrar ese programa o cambiar el puerto en XAMPP.

5. Firewall bloquea conexion del celular
   -> Permitir Apache en el Firewall de Windows.

6. config.php falla con error de conexion MySQL
   -> Si tu MySQL tiene password, cambiar $pass = ""
      por tu password en config.php.

7. No da ubicacion GPS
   -> En el emulador seguir las instrucciones del Paso 3.


NOTAS
------
- Cada persona necesita su propia instancia de XAMPP corriendo.
- La base de datos es local, no se comparten datos entre equipos.
- La carpeta "uploads" no se sube a GitHub (las fotos son locales).