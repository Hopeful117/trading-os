# Implémentation ADR-024 — Broker Credential Management

## Frontières

- Trading Core possède `BrokerAccount`, son propriétaire et son état métier.
- Broker Service reçoit temporairement les credentials, contrôle leur format,
  valide Kraken, chiffre et gère les versions.
- Secret Management est exposé uniquement par les ports `SecretWriter`,
  `SecretReader`, `SecretRotator` et `SecretRevoker`.
- Gateway route uniquement les commandes `credentials`, `validate` et
  `connection-status` vers Broker Service. Les lectures de compte restent dans
  Trading Core.

Trading Core ne reçoit jamais `CredentialMaterial`. Les callbacks du Broker
Service ne contiennent que le statut, l'identité externe éventuelle et une
référence opaque.

## Stockage et chiffrement

Flyway crée `broker_connection` et `broker_secret` dans la base dédiée au Broker
Service. Une contrainte PostgreSQL garantit au maximum une version `ACTIVE` par
compte. La rotation verrouille la version active, écrit une version `PENDING`,
révoque l'ancienne puis active la nouvelle dans la même transaction.

`AesGcmSecretCipher` utilise AES-256-GCM, un IV aléatoire de 96 bits et un tag de
128 bits. La clé maître et sa version viennent exclusivement de
l'environnement. Une clé absente, non Base64 ou d'une taille différente de
256 bits empêche le démarrage du mode `stored`.

## Validation Kraken

L'adapter réalise uniquement des appels privés non destructifs :

- `Balance` ;
- `OpenPositions` ;
- `OpenOrders` ;
- `TradesHistory`.

Kraken ne fournit pas un endpoint unique et fiable listant les permissions.
La V1 déduit donc les permissions de lecture des probes réussis. Aucun ordre,
retrait, transfert ou opération de financement n'est effectué.

## Cohérence distribuée

Le JWT utilisateur est validé par Broker Service puis propagé à Trading Core
pour vérifier la propriété. Les changements de statut sont des appels
synchrones. La transaction Broker Service est annulée si le callback final
échoue, ce qui conserve l'ancienne version active pendant une rotation.

## Configuration

Production :

```text
BROKER_CREDENTIAL_SOURCE=stored
BROKER_MASTER_KEY=<32 octets aléatoires encodés en Base64>
BROKER_MASTER_KEY_VERSION=v1
BROKER_DATABASE_URL=jdbc:postgresql://broker-db:5432/broker_service
```

Le mode `environment` est conservé pour dev/test/demo. Il est refusé avec le
profil `prod` sans opt-in explicite et ne doit pas être mélangé au stockage
multi-utilisateur.
