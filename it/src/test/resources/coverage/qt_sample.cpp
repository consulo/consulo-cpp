#include <QDebug>
#include <QHash>
#include <QList>
#include <QObject>
#include <QString>
#include <QStringList>

class Counter : public QObject {
    Q_OBJECT

public:
    explicit Counter(QObject* parent = nullptr) : QObject(parent) {}

    int value() const { return value_; }

public slots:
    void setValue(int value) {
        if (value != value_) {
            value_ = value;
            emit valueChanged(value);
        }
    }

signals:
    void valueChanged(int value);

private:
    int value_ = 0;
};

int run() {
    Counter counter;
    QObject::connect(&counter, &Counter::valueChanged, [](int value) { qDebug() << value; });
    counter.setValue(3);  //> setValue -> Counter

    QString name = QStringLiteral("consulo");
    QString upper = name.toUpper();  //> toUpper -> QString
    QStringList parts = upper.split(QLatin1Char('O'));
    for (const QString& part : parts) {
        qDebug() << part.length();  //> length -> QString
    }

    QList<int> numbers{1, 2, 3};
    numbers.append(4);
    QHash<QString, int> counts;
    counts.insert(name, numbers.size());
    return counts.value(name) + counter.value();
}
