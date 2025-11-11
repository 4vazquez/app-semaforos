// Coordenadas de Monterrey
const map = L.map('map').setView([25.6866, -100.3161], 12);

// Capa base (OpenStreetMap)
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '© OpenStreetMap contributors'
}).addTo(map);

// Ejemplo: marcador de prueba
L.marker([25.6866, -100.3161]).addTo(map)
  .bindPopup('Centro de Monterrey')
  .openPopup();