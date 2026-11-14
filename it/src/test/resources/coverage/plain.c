#include <errno.h>
#include <math.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>

#define MAX_ITEMS 16
#define SQUARE(x) ((x) * (x))

typedef enum { SHAPE_CIRCLE, SHAPE_RECT } shape_kind;

typedef struct point {
    double x;
    double y;
} point_t;

struct shape {
    shape_kind kind;
    point_t origin;
    union {
        double radius;
        struct {
            double width;
            double height;
        } size;
    } u;
    struct shape *next;
};

typedef double (*area_fn)(const struct shape *);

static double circle_area(const struct shape *s) {
    return M_PI * SQUARE(s->u.radius);
}

static double rect_area(const struct shape *s) {
    return s->u.size.width * s->u.size.height;
}

static const area_fn AREAS[] = {circle_area, rect_area};

struct list {
    struct shape items[MAX_ITEMS];
    size_t count;
    pthread_mutex_t lock;
};

static bool list_add(struct list *list, struct shape shape) {
    if (list->count >= MAX_ITEMS) {
        errno = ENOMEM;
        return false;
    }
    pthread_mutex_lock(&list->lock);
    list->items[list->count++] = shape;
    pthread_mutex_unlock(&list->lock);
    return true;
}

static int compare(const void *a, const void *b) {
    const struct shape *left = a;
    const struct shape *right = b;
    return (int) (AREAS[left->kind](left) - AREAS[right->kind](right));
}

int main(int argc, char **argv) {
    struct list list;
    memset(&list, 0, sizeof list);
    pthread_mutex_init(&list.lock, NULL);

    struct shape circle = {.kind = SHAPE_CIRCLE, .origin = {0, 0}, .u.radius = 2.0, .next = NULL};
    struct shape rect = {.kind = SHAPE_RECT, .origin = {1, 1}};
    rect.u.size.width = 3;
    rect.u.size.height = 4;
    list_add(&list, circle);
    list_add(&list, rect);

    qsort(list.items, list.count, sizeof(struct shape), compare);

    for (size_t i = 0; i < list.count; i++) {
        struct shape *s = &list.items[i];
        printf("%d %.2f at (%.1f, %.1f)\n", s->kind, AREAS[s->kind](s), s->origin.x, s->origin.y);
    }

    struct stat info;
    if (argc > 1 && stat(argv[1], &info) == 0) {
        printf("%lld bytes\n", (long long) info.st_size);
    }

    char *copy = strdup(argc > 0 ? argv[0] : "none");
    uint32_t length = (uint32_t) strlen(copy);
    free(copy);

    time_t now = time(NULL);
    struct tm *local = localtime(&now);
    fprintf(stderr, "%d %u %s\n", local->tm_year, length, strerror(errno));

    pthread_mutex_destroy(&list.lock);
    return EXIT_SUCCESS;
}
