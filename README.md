# TP — Commandes asynchrones avec Spring Boot et RabbitMQ

Projet Java 17 / Spring Boot 3.3.2 : Gateway (8080), Product (8081), Client (8082), Order (8083).

## Démarrage

Démarrer RabbitMQ avec sa console de gestion et les ports 5672 et 15672. Dans des terminaux séparés, depuis la racine du projet :

```bash
mvn -pl client spring-boot:run
```

```bash
mvn -pl order spring-boot:run
```

```bash
mvn -pl product spring-boot:run
```

```bash
mvn -pl api-gateway spring-boot:run
```

Pour lancer Order ou Product depuis l’IDE, utiliser le dossier de son module comme répertoire de travail. Les bases H2 sur fichier sont relatives à ce dossier : `order/data/orderdb` et `product/data/productdb`. Elles conservent les commandes, le stock et les résultats déjà traités après redémarrage. Le passage initial depuis H2 en mémoire crée des bases neuves. Les produits sont initialisés seulement s’ils sont absents.

## Flux

1. Order vérifie le client auprès de Client, enregistre la commande PENDING et publie OrderCreatedEvent.
2. Product reçoit la commande, vérifie tous les produits puis réserve le stock dans une transaction.
3. Product mémorise le résultat et publie StockReservedEvent ou StockRejectedEvent.
4. Order reçoit le résultat, passe à CONFIRMED ou REJECTED et complète les lignes et le total en cas de confirmation.

La réponse au POST est 202 Accepted avec PENDING. Tant que Product n’a pas confirmé, les lignes de la commande sont vides et le total vaut zéro.

| Exchange | Queue | Routing key |
| --- | --- | --- |
| orders.exchange | order.created.queue | order.created |
| products.exchange | order.stock-result.queue | stock.* |

Un message de commande déjà traité republie son résultat enregistré sans nouvelle réservation. Un résultat déjà appliqué à Order n’ajoute pas de nouveau les lignes. Les exceptions de traitement dans Product déclenchent au maximum trois tentatives, puis le message est envoyé à `order.created.queue.dlq`. Un refus de stock est un résultat métier, pas une erreur technique à retenter.

## Vérifications du TP

```bash
curl -i -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"clientId":1,"items":[{"productId":1,"quantity":2}]}'
```

Noter l’identifiant retourné, puis consulter la commande :

```bash
curl http://localhost:8080/orders/1
```

Adapter l’identifiant à celui du POST. Le statut doit passer de PENDING à CONFIRMED si le stock est suffisant. Une quantité supérieure au stock conduit à REJECTED.

Pour vérifier un doublon, republier le JSON de OrderCreatedEvent dans la console RabbitMQ, sur `orders.exchange`, avec la routing key `order.created` et le content type `application/json`. Conserver le même orderId. Le stock doit rester inchangé. Dans Get messages, utiliser un acquittement qui remet le message dans la queue pour ne pas le retirer avant son traitement.

Avec Product arrêté, créer une commande : elle reste PENDING et le message attend dans la queue. Redémarrer Product : il réserve le stock et la commande se confirme si le stock est suffisant. Rejouer un ancien événement après redémarrage ne réserve pas une seconde fois.

## Comparaison avec la version synchrone

Dans la version initiale, Order appelait Product par HTTP pour vérifier les produits et réserver le stock pendant la requête. Si Product était arrêté, la création échouait. Dans cette version, Order accepte la commande même si Product est arrêté, puis le traitement reprend quand Product redémarre. La vérification du client reste synchrone : Client doit être disponible.

## Console RabbitMQ

http://localhost:15672 — identifiant guest, mot de passe guest pour la configuration locale du TP.
