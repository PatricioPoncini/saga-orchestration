# Despliegue en un VPS

Resultado: la demo en `https://saga.patricioponcini.com.ar` (o el subdominio que elijas), con HTTPS
automático, levantada con **un solo comando**: `./scripts/deploy.sh`.

```
Internet ──▶ (Cloudflare, opcional) ──▶ Caddy :443 ──▶ nginx (UI) ──▶ servicios ──▶ Postgres / RabbitMQ
                                        HTTPS, headers    límites por IP   red interna, sin acceso desde afuera
```

## 1. El VPS

- Ubuntu 24.04 LTS (o Debian 12).
- **4 GB de RAM** recomendados: son 4 JVM + Postgres + RabbitMQ (cada contenedor tiene un tope de memoria).
  Con 2 GB funciona agregando 2 GB de swap (paso 2).
- 2 vCPU y 20 GB de disco alcanzan.

## 2. Preparar el servidor (una sola vez)

Conectado por SSH como root (o con sudo):

```bash
# Actualizaciones automáticas de seguridad
apt update && apt upgrade -y
apt install -y unattended-upgrades fail2ban ufw git
dpkg-reconfigure -plow unattended-upgrades

# Usuario propio (no trabajar como root)
adduser patricio
usermod -aG sudo patricio
mkdir -p /home/patricio/.ssh && cp ~/.ssh/authorized_keys /home/patricio/.ssh/
chown -R patricio:patricio /home/patricio/.ssh

# Docker (script oficial)
curl -fsSL https://get.docker.com | sh
usermod -aG docker patricio

# Firewall: sólo SSH, HTTP y HTTPS
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw enable

# (Sólo si el VPS tiene 2 GB) swap de 2 GB
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

**SSH sólo con clave** (antes de esto, verificá que podés entrar con tu clave como `patricio`):
en `/etc/ssh/sshd_config` poné `PasswordAuthentication no` y `PermitRootLogin no`, y después
`systemctl restart ssh`. `fail2ban` ya bloquea IPs que prueban contraseñas por SSH con su configuración por defecto.

## 3. DNS del subdominio

Tu dominio ya usa Cloudflare. En el panel de Cloudflare → DNS → *Add record*:

| Tipo | Nombre | Contenido | Proxy |
|---|---|---|---|
| A | `saga` | IP del VPS | **DNS only (nube gris)** para el primer despliegue |
| AAAA | `saga` | IPv6 del VPS (si tiene) | igual |

Verificá desde tu máquina: `dig +short saga.patricioponcini.com.ar` debe devolver la IP del VPS.

## 4. Desplegar

Como `patricio` en el VPS:

```bash
git clone <url-del-repo> sagalab && cd sagalab
./scripts/deploy.sh
```

La primera vez pregunta el subdominio, el email (para Let's Encrypt) y cada cuánto se reinicia la demo, y
crea `.env` con contraseñas aleatorias para la base y RabbitMQ. La primera compilación tarda unos minutos.
Al final muestra `✓ Listo: https://saga.patricioponcini.com.ar`.

**Actualizar** después de cambiar el código: `./scripts/deploy.sh --pull`.

Comandos útiles:

```bash
docker compose -f docker-compose.prod.yml ps                     # estado
docker compose -f docker-compose.prod.yml logs -f caddy ui       # logs (también saga-orchestrator, etc.)
docker compose -f docker-compose.prod.yml down                   # bajar (conserva datos y certificados)
```

> Guardá una copia del `.env`: la contraseña de la base sólo se fija la primera vez que se crea el volumen.

## 5. (Recomendado) Activar Cloudflare adelante

Con el sitio ya andando por HTTPS:

1. En el registro DNS de `saga`, cambiá a **Proxied (nube naranja)**.
2. SSL/TLS → modo **Full (strict)**.
3. SSL/TLS → Edge Certificates → **Always Use HTTPS: Off**. Caddy ya redirige a HTTPS, y así la
   renovación del certificado de Let's Encrypt (que usa el puerto 80) sigue funcionando a través de Cloudflare.
4. En el `.env`, reemplazá `TRUSTED_PROXIES=private_ranges` por los rangos de Cloudflare
   (lista actualizada en https://www.cloudflare.com/ips/) y volvé a correr `./scripts/deploy.sh`.
   Sin esto, los límites por IP verían a Cloudflare como si fuera un único visitante:

   ```
   TRUSTED_PROXIES=173.245.48.0/20 103.21.244.0/22 103.22.200.0/22 103.31.4.0/22 141.101.64.0/18 108.162.192.0/18 190.93.240.0/20 188.114.96.0/20 197.234.240.0/22 198.41.128.0/17 162.158.0.0/15 104.16.0.0/13 104.24.0.0/14 172.64.0.0/13 131.0.72.0/22
   ```
5. Security → **Bot Fight Mode: On**.
6. (Opcional) Security → WAF → Rate limiting rules: una regla gratuita, por ejemplo bloquear 10 s a una IP
   que haga más de 50 requests en 10 s a `/api/`.

Con esto la IP del VPS deja de ser pública en el DNS, y Cloudflare absorbe ataques de volumen antes de que
lleguen al servidor.

## Qué protege a la demo (y qué no)

| Capa | Qué hace |
|---|---|
| Firewall (ufw) | Sólo 22, 80 y 443. |
| Docker | Sólo Caddy publica puertos. Base, RabbitMQ y servicios están en una red `internal` sin acceso desde internet (ni salida hacia ella). |
| Caddy | HTTPS automático, redirección HTTP→HTTPS, headers de seguridad (HSTS, CSP, X-Frame-Options…). |
| nginx | Límites por IP: lecturas 10/s (ráfagas de 40), escrituras 30/min (ráfagas de 10), 4 conexiones en vivo (SSE). Más allá: `429 Too Many Requests`. Bloquea `/actuator` y el endpoint de reposición de stock. Cuerpos de hasta 8 KB. |
| Servicios | Validan los datos (cantidad, monto, ids → `400`). Las fallas inyectadas tienen topes (la demora máxima es 15 s). |
| Reinicio automático | Cada `DEMO_RESET_INTERVAL` se vacían todas las tablas y colas: nadie puede llenar la base. |
| Cloudflare (opcional) | Oculta la IP del VPS, mitiga DDoS y bots, permite reglas de rate limiting en el borde. |

## Muchas personas a la vez

Cada visitante corre sus escenarios en paralelo sin afectar a los demás: las fallas de un escenario viajan
con su saga (headers del mensaje), no son un estado global de los servicios. Probado con 100 sagas
simultáneas mezclando todos los escenarios: las que no tenían fallas terminaron en ~1 s, sin timeouts ni
duplicados, y cada navegador recibió por SSE sólo los eventos de su propia saga.
