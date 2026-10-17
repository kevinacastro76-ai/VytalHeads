# VytalHeads

Plugin de cabezas decorativas para servidores **Paper 1.21.x**. Código propio, menú por categorías, búsqueda, paginación, permisos, comandos administrativos y almacenamiento local JSON.

## Requisitos
- Java 21
- Gradle 8.10+ (solo para compilar localmente)
- Paper 1.21.x. El proyecto se compila contra Paper 1.21.4; comprueba compatibilidad en la subversión exacta de tu servidor.

## Compilar y descargar
1. Sube **todo el contenido de este ZIP** a la raíz de un repositorio GitHub.
2. Abre **Actions** y habilita GitHub Actions si GitHub lo solicita.
3. Ve a **Actions → Build VytalHeads → Run workflow** (o haz `push` a `main`).
4. Abre la ejecución verde y busca **Artifacts → VytalHeads-JAR**.
5. Descarga el ZIP del artefacto; dentro estará `VytalHeads-1.0.0.jar`.
6. Coloca el JAR en `plugins/` de tu servidor Paper y reinicia.

## Comandos
- `/heads` — abre el menú.
- `/heads <categoria>` — abre una categoría.
- `/heads search <texto>` — busca por nombre.
- `/heads give <jugador> <id>` — entrega una cabeza guardada por ID.
- `/heads add <id> <categoria> <nombre>` — guarda la cabeza que tienes en la mano (admin).
- `/heads reload` — recarga la base de datos.

## Permisos
- `vytalheads.use` — abrir el menú (por defecto: todos).
- `vytalheads.admin` — comandos administrativos (por defecto: op).
- `vytalheads.give` — entregar cabezas a jugadores (por defecto: op).

## Base de datos y actualizaciones
En el primer arranque se crea `plugins/VytalHeads/heads.json`. Las cabezas añadidas se guardan ahí y sobreviven reinicios. El proyecto incluye un archivo `data/heads.json` con ejemplos pequeños para probar el sistema; no incluye decenas de miles de texturas ni una colección de terceros. El comando `add` permite poblar tu base local con cabezas que tengas en la mano. La actualización automática de una colección remota no viene activada en esta versión: para implementarla con seguridad hace falta definir una fuente de datos autorizada y su formato. No descarga ni copia automáticamente la base de HeadDatabase.

## Limitaciones de esta primera versión
- Compatible con la API de Paper, no con proxy Velocity ni con Forge/Fabric como plugin del servidor.
- Las cabezas con textura se guardan con los datos serializados del ItemStack en Base64; el formato de serialización de Bukkit puede variar entre versiones mayores.
- El buscador es un diálogo por chat mediante el comando `/heads search <texto>`, además del menú de categorías.
- 
