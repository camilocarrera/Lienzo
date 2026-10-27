# Lienzo

Launcher de Android que arranca en blanco. La persona arma su pantalla con bloques.

## Cómo usarlo

- Mantener presionado en un espacio vacío: agregar reloj, nota, atajo a una app o cambiar el fondo.
- Arrastrar un bloque para moverlo.
- Tocar una nota para editarla. Mantener presionado sobre cualquier bloque para quitarlo.
- Deslizar hacia arriba: lista de todas las apps, con buscador.

## Cómo probarlo en el celular

1. Abrir esta carpeta en Android Studio y esperar a que sincronice Gradle.
2. En el celular: activar Opciones de desarrollador y Depuración USB, y conectarlo por cable.
3. Presionar Run. Se instala la app llamada Lienzo.
4. Presionar el botón de Inicio y elegir Lienzo como aplicación de inicio ("Siempre").

Para volver al launcher original: Ajustes, Aplicaciones, Aplicaciones predeterminadas, Aplicación de inicio.

## Notas

- No usa ninguna librería externa ni servicios de Google, así que funciona en celulares sin Play Store.
- Los bloques se guardan en el propio celular (SharedPreferences).
- Versión mínima de Android: 7.0 (API 24).
