// Inicializar mapa
const map = L.map('map').setView([25.6866, -100.3161], 12);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '© OpenStreetMap contributors'
}).addTo(map);

// Simulación de cambio de luces
const lightStatus = document.getElementById('light-status');
const colors = ['Rojo', 'Verde', 'Amarillo'];
let index = 0;
setInterval(() => {
    index = (index + 1) % colors.length;
    lightStatus.textContent = colors[index];
}, 5000);

// Control de opacidad
const widget = document.getElementById('widget');
document.getElementById('opacity-control').addEventListener('input', (e) => {
    widget.style.opacity = e.target.value;
});

// Hacer el widget arrastrable
widget.addEventListener('mousedown', function(e) {
    let offsetX = e.clientX - widget.offsetLeft;
    let offsetY = e.clientY - widget.offsetTop;

    function mouseMoveHandler(e) {
        widget.style.left = (e.clientX - offsetX) + 'px';
        widget.style.top = (e.clientY - offsetY) + 'px';
    }

    function mouseUpHandler() {
        document.removeEventListener('mousemove', mouseMoveHandler);
        document.removeEventListener('mouseup', mouseUpHandler);
    }

    document.addEventListener('mousemove', mouseMoveHandler);
    document.addEventListener('mouseup', mouseUpHandler);
});
// Inicializar mapa
const map = L.map('map').setView([25.6866, -100.3161], 12);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '© OpenStreetMap contributors'
}).addTo(map);

// Cargar GeoJSON de semáforos
fetch('semaforos.geojson')
    .then(response => response.json())
    .then(data => {
        L.geoJSON(data, {
            pointToLayer: function (feature, latlng) {
                return L.circleMarker(latlng, {
                    radius: 6,
                    fillColor: "#ff0000",
                    color: "#fff",
                    weight: 1,
                    opacity: 1,
                    fillOpacity: 0.8
                }).bindPopup("Semáforo");
            }
        }).addTo(map);
    });