import java.util.*;
import java.io.*;
import java.math.*;

/**
 * Auto-generated code below aims at helping you parse the standard input
 * according to the problem statement.
 **/
class Player {

    static class Base {
        final Position pos;
        int health;
        int mana;

        Base(int x, int y) {
            pos = new Position(x, y);
        }
    }

    static class Game {
        final HashMap<Integer, Entity> entities = new HashMap<>();
        final HashMap<Integer, Monster> monsters = new HashMap<>();
        final ArrayList<Hero> heroes;
        final int heroCount;
        final Base base;
        final Base baseOp;
        int turn = 0;

        Game(int x, int y, int n) {
            base = new Base(x, y);
            baseOp = (y < 4500) ? new Base(17630, 9000) : new Base(0, 0);
            System.err.println("base = " + base.pos);
            System.err.println("baseOp = " + baseOp.pos);
            heroCount = n;
            heroes = new ArrayList<>(heroCount);
        }

        void updateBase(int baseIdx, int health, int mana) {
            if (baseIdx == 0) {
                base.health = health;
                base.mana = mana;
            }
            monsters.clear();
        }

        Monster updateMonster(int id, int x, int y, int vx, int vy, int health, int shield) {
            Monster m = monsters.get(id);
            if (m == null) {
                m = (Monster) entities.get(id);
                if (m == null) {
                    m = new Monster(id);
                    entities.put(m.id, m);
                }
                monsters.put(m.id, m);
            }
            m.update(x, y, vx, vy, health, shield);
            return m;
        }

        void updateHero(int id, int x, int y, int shield, boolean controlled) {
            Hero h = null;
            for (int i = 0; i < heroes.size(); i += 1) {
                Hero hh = heroes.get(i);
                if (hh.id == id) {
                    h = hh;
                    break;
                }
            }
            if (h == null) {
                h = (Hero) entities.get(id);
                if (h == null) {
                    h = new Hero(id);
                    entities.put(h.id, h);
                }
                heroes.add(h);
            }
            h.pos.x = x;
            h.pos.y = y;
        }

        Hero getHero(int idx) {
            return heroes.get(idx);
        }

        void think() {
            if (turn > 100)
                thinkOffensive();
            else
                thinkDefensive();

            turn += 1;
        }

        void thinkOffensive() {
            Hero a = null;
            ArrayList<Hero> list = new ArrayList<>(heroes);
            for (Hero h : list)
                if (h.goal == Goal.ATTACK)
                    a = h;
            if (a == null)
                a = getClosest(list, baseOp.pos.x, baseOp.pos.y);
            list.remove(a);
            Hero top = list.remove(1);
            Hero bot = list.remove(0);

            //System.err.println("a " + a.id + " t " + top.id + " b " + bot.id);

            final Vector2D v = new Vector2D();
            // spread defence
            {
                v.set(baseOp.pos);
                v.subtract(base.pos);
                v.normalize();
                v.multiply(5000);

                if (top.goal == Goal.IDLE) {
                    Vector2D dir = v.getRotatedBy(Math.toRadians(25));
                    dir.add(base.pos);
                    top.pushTo(dir);
                }

                if (bot.goal == Goal.IDLE) {
                    Vector2D dir = v.getRotatedBy(Math.toRadians(-10));
                    dir.add(base.pos);
                    bot.pushTo(dir);
                }

            }
            top.think(this);
            bot.think(this);

            // move attacker in enemy base
            {
                v.set(base.pos);
                v.subtract(baseOp.pos);
                v.normalize();
                v.multiply(2000);
                v.rotateBy(Math.toRadians(15));
                a.attackAt(baseOp.pos);
            }
            a.think(this);
        }

        void thinkDefensive() {
            // set main goal
            spread();

            // update heroes
            getHero(0).think(this);
            getHero(1).think(this);
            getHero(2).think(this);

            // protect base
            Collection<Monster> baseAttackers = getBaseAttackers();
            for (Monster m : baseAttackers) {
                int radiusSq = Monster.BASE_TARGET_DISTANCE * Monster.BASE_TARGET_DISTANCE;
                Vector2D S1;
                Vector2D h = Vector2D.subtract(base.pos, m.pos); // h=r.o-c.M
                if (h.getLengthSq() > radiusSq) {
                    Vector2D e = new Vector2D(m.move.x, m.move.y); // e=ray.dir
                    e.normalize(); // e=g/|g|
                    double lf = e.dot(h); // lf=e.h
                    double s = radiusSq - h.dot(h) + lf * lf; // s=r^2-h^2+lf^2
                    if (s < 0)
                        continue; // no intersection points ?
                    s = Math.sqrt(s); // s=sqrt(r^2-h^2+lf^2)

                    // int result = 0;
                    if (lf < s) // S1 behind A ?
                    {
                        if (lf + s >= 0) // S2 before A ?}
                        {
                            s = -s; // swap S1 <-> S2}
                            // result = 1; // one intersection point
                        }
                    } // else result = 2; // 2 intersection points

                    S1 = e.getMultiplied(lf - s);
                    S1.add(m.pos); // S1=A+e*(lf-s)
                } else {
                    S1 = new Vector2D(m.pos.x, m.pos.y);
                    S1.add(m.move);
                }

                // S1 is now the position we expect the monster to start targeting the base
                ArrayList<Hero> list = new ArrayList<>(heroes);
                while (!list.isEmpty()) {
                    Hero hero = getClosest(list, (int) S1.x, (int) S1.y);
                    if (hero.follow == m || hero.goal != Goal.DEFEND) {
                        hero.kill(m, (float) S1.x, (float) S1.y);
                        break;
                    }
                    list.remove(hero);
                }
            }
        }

        Collection<Monster> getBaseAttackers() {
            ArrayList<Monster> list = new ArrayList<>();

            Vector2D proj;
            final Vector2D monsterDir = new Vector2D();
            final Vector2D baseDir = new Vector2D();
            for (Monster m : monsters.values()) {
                monsterDir.set(m.move.x, m.move.y);
                baseDir.set(base.pos.x - m.pos.x, base.pos.y - m.pos.y);
                proj = Vector2D.getProjectedVector(monsterDir, baseDir);
                proj.add(m.pos.x, m.pos.y);
                int dist = (int) proj.distance(base.pos.x, base.pos.y);
                if (dist <= Monster.BASE_TARGET_DISTANCE) {
                    list.add(m);
                }
            }

            return list;
        }

        // spread to increase vision
        void spread() {
            Hero top = getHero(0);
            Hero mid = getHero(1);
            Hero bot = getHero(2);

            final Vector2D v = new Vector2D();
            v.set(baseOp.pos.x, baseOp.pos.y);
            v.subtract(base.pos.x, base.pos.y);
            v.normalize();
            v.multiply(6000);

            if (top.goal == Goal.IDLE) {
                Vector2D dir = v.getRotatedBy(Math.toRadians(45));
                dir.add(base.pos);
                top.pushTo(dir);
            }

            if (mid.goal == Goal.IDLE) {
                Vector2D dir = v.getRotatedBy(Math.toRadians(15));
                dir.add(base.pos);
                mid.pushTo(dir);
            }
            if (bot.goal == Goal.IDLE) {
                Vector2D dir = v.getRotatedBy(Math.toRadians(-15));
                dir.add(base.pos);
                bot.pushTo(dir);
            }
        }

        <T extends Entity> T getClosest(Collection<T> list, int x, int y) {
            final Vector2D v = new Vector2D();
            T near = null;
            double nearDistSq = Double.MAX_VALUE;
            for (T m : list) {
                v.set(base.pos.x, base.pos.y);
                double distSq = v.distanceSq(m.pos.x, m.pos.y);
                if (nearDistSq > distSq) {
                    nearDistSq = distSq;
                    near = m;
                }
            }

            return near;
        }

        Monster getClosestMonster(Position pos) {
            return getClosest(monsters.values(), pos.x, pos.y);
        }

        Hero getClosestHero(Position pos) {
            return getClosest(heroes, pos.x, pos.y);
        }
    }

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        int baseX = in.nextInt();
        int baseY = in.nextInt();
        int heroesPerPlayer = in.nextInt();

        Game g = new Game(baseX, baseY, heroesPerPlayer);
        Position pos = new Position();

        // game loop
        while (true) {
            for (int i = 0; i < 2; i++) {
                int health = in.nextInt();
                int mana = in.nextInt();

                g.updateBase(i, health, mana);
            }
            int entityCount = in.nextInt();
            for (int i = 0; i < entityCount; i++) {
                int id = in.nextInt();
                int type = in.nextInt();
                int x = in.nextInt();
                int y = in.nextInt();
                int shieldLife = in.nextInt();
                int isControlled = in.nextInt();
                int health = in.nextInt();
                int vx = in.nextInt();
                int vy = in.nextInt();
                int nearBase = in.nextInt();
                int threatFor = in.nextInt();

                switch (type) {
                    case 0:
                        // monster
                        Monster m = g.updateMonster(id, x, y, vx, vy, health, shieldLife);
                        m.nearBase = nearBase != 0;
                        break;
                    case 1:
                        // hero
                        g.updateHero(id, x, y, shieldLife, isControlled != 0);
                        break;
                    case 2:
                        // opponent hero
                        break;
                }
            }

            g.think();

            for (int i = 0; i < heroesPerPlayer; i++) {

                // Write an action using System.out.println()
                // To debug: System.err.println("Debug messages...");

                Hero h = g.getHero(i);
                switch (h.getAction()) {
                    case WAIT:
                        System.out.println("WAIT");
                        break;
                    case MOVE:
                        h.getTargetPos(pos);
                        System.out.println("MOVE " + pos.x + " " + pos.y + " " + h.debug);
                        break;
                    case SPELL:
                        Spell s = h.getSpell();
                        System.out.println("SPELL " + s.cast());
                        break;
                }

            }
        }
    }

    enum Action {
        WAIT, MOVE, SPELL
    }

    enum Goal {
        PUSH_TO, PATROL, IDLE, DEFEND, ATTACK
    }

    static class Position {
        int x;
        int y;

        Position() {
            this(-1, -1);
        }

        Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "(" + x + " " + y + ")";
        }
    }

    static class Spell {
        static final int WIND = 1;
        static final int SHIELD = 2;
        static final int CONTROL = 3;
        static final int WIND_EFFECT = 1280;
        int spellType = 0;
        Position pos;
        int entityId = -1;

        Spell() {
        }

        void wind(int x, int y) {
            spellType = WIND;
            pos.x = x;
            pos.y = y;
        }

        void shield(int entityId) {
            spellType = SHIELD;
            this.entityId = entityId;
        }

        void control(int entityId, int x, int y) {
            spellType = CONTROL;
            this.entityId = entityId;
            pos.x = x;
            pos.y = y;
        }

        String cast() {
            switch (spellType) {
                case WIND:
                    return "WIND " + pos.x + " " + pos.y + " W";

                case SHIELD:
                    return "SHIELD " + entityId + " S " + entityId;

                case CONTROL:
                    return "CONTROL " + entityId + " " + pos.x + " " + pos.y + " C " + entityId;
            }
            return null;
        }
    }

    static abstract class Entity {
        int id;
        final Position pos = new Position();

        Entity(int id) {
            this.id = id;
        }
    }

    /**
     * HERO
     */

    static class Hero extends Entity {
        static final int SPEED = 800;
        static final int VISION = 2200;
        static final int KILL_RADIUS = 800;

        final Vector2D dest = new Vector2D();
        Action act = Action.WAIT;
        Goal goal = Goal.IDLE;
        String debug = "";
        Monster follow = null;
        Spell spell = null;

        Hero(int id) {
            super(id);
        }

        Action getAction() {
            return act;
        }

        void getTargetPos(Position target) {
            target.x = (int) dest.x;
            target.y = (int) dest.y;
        }

        Spell getSpell() {
            if (spell == null) {
                spell = new Spell();
                spell.shield(id);
            }
            // spell.wind(17630 / 2, 9000 / 2);
            return spell;
        }

        void pushTo(Position push) {
            pushTo(push.x, push.y);
        }

        void pushTo(Vector2D push) {
            pushTo(push.x, push.y);
        }

        void pushTo(double x, double y) {
            goal = Goal.PUSH_TO;
            dest.set(x, y);
            if (inKillRadius()) {
                goal = Goal.IDLE;
                debug = "idle";
            }
        }

        void think(Game g) {
            if (goal == Goal.PUSH_TO) {
                Monster m = g.getClosestMonster(pos);
                if (m == null) {
                    if (inKillRadius()) {
                        goal = Goal.IDLE;
                        debug = "idle";
                    } else {
                        // go to dest
                        act = Action.MOVE;
                    }
                } else {
                    // see if we should target monster instead
                    Vector2D v = new Vector2D(m.pos.x, m.pos.y);
                    if (v.distance(pos.x, pos.y) < 2000) {
                        act = Action.MOVE;
                        dest.x = m.pos.x + m.move.x;
                        dest.y = m.pos.y + m.move.y;
                        debug = "agro " + m.id;
                        follow = m;
                    }
                }
            } else if (goal == Goal.DEFEND) {
                if (follow != null && g.monsters.containsKey(follow.id)) {
                    dest.set(follow.pos);
                } else {
                    goal = Goal.IDLE;
                    follow = null;
                    debug = "idle";
                }
            } else if (goal == Goal.ATTACK) {
                if (inKillRadius()) {
                    act = Action.SPELL;

                }
            }
        }

        void kill(Monster m, float x, float y) {
            goal = Goal.DEFEND;
            act = Action.MOVE;
            dest.set(x, y);
            debug = "kill";
            if (inKillRadius() || m.nearBase) {
                follow = m;
                dest.set(m.pos.x, m.pos.y);
                debug = "kill_f";
            }
        }

        boolean inKillRadius() {
            double dx = pos.x - dest.x;
            double dy = pos.y - dest.y;
            return (dx * dx + dy * dy) < (KILL_RADIUS * KILL_RADIUS);
        }

        void attackAt(Position pos) {
            goal = Goal.ATTACK;
            dest.set(pos);
            debug = "";
        }
    }

    /**
     * MONSTER
     */

    static class Monster extends Entity {
        static final int SPEED = 400;
        static final int BASE_TARGET_DISTANCE = 5000;
        boolean nearBase = false;
        final Position move = new Position();

        Monster(int id) {
            super(id);
        }

        void update(int x, int y, int vx, int vy, int health, int shield) {
            pos.x = x;
            pos.y = y;
            move.x = vx;
            move.y = vy;
        }
    }

    /**
     * VECTOR 2D
     */

    static class Vector2D {

        public double x;
        public double y;

        public Vector2D() {
        }

        public Vector2D(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public Vector2D(Vector2D v) {
            set(v);
        }

        public void set(Position pos) {
            x = pos.x;
            y = pos.y;
        }

        public void set(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public void set(Vector2D v) {
            this.x = v.x;
            this.y = v.y;
        }

        public void setZero() {
            x = 0;
            y = 0;
        }

        // public double[] getComponents() {
        // return new double[]{x, y};
        // }

        public double getLength() {
            return Math.sqrt(x * x + y * y);
        }

        public double getLengthSq() {
            return (x * x + y * y);
        }

        public double distanceSq(double vx, double vy) {
            vx -= x;
            vy -= y;
            return (vx * vx + vy * vy);
        }

        public double distanceSq(Vector2D v) {
            double vx = v.x - this.x;
            double vy = v.y - this.y;
            return (vx * vx + vy * vy);
        }

        public double distance(double vx, double vy) {
            vx -= x;
            vy -= y;
            return Math.sqrt(vx * vx + vy * vy);
        }

        public double distance(Vector2D v) {
            double vx = v.x - this.x;
            double vy = v.y - this.y;
            return Math.sqrt(vx * vx + vy * vy);
        }

        public double getAngle() {
            return Math.atan2(y, x);
        }

        public void normalize() {
            double magnitude = getLength();
            x /= magnitude;
            y /= magnitude;
        }

        public Vector2D getNormalized() {
            double magnitude = getLength();
            return new Vector2D(x / magnitude, y / magnitude);
        }

        public static Vector2D toCartesian(double magnitude, double angle) {
            return new Vector2D(magnitude * Math.cos(angle), magnitude * Math.sin(angle));
        }

        public void add(Position pos) {
            x += pos.x;
            y += pos.y;
        }

        public void add(Vector2D v) {
            this.x += v.x;
            this.y += v.y;
        }

        public void add(double vx, double vy) {
            this.x += vx;
            this.y += vy;
        }

        public static Vector2D add(Vector2D v1, Vector2D v2) {
            return new Vector2D(v1.x + v2.x, v1.y + v2.y);
        }

        public Vector2D getAdded(Vector2D v) {
            return new Vector2D(this.x + v.x, this.y + v.y);
        }

        public void subtract(Vector2D v) {
            this.x -= v.x;
            this.y -= v.y;
        }

        public void subtract(double vx, double vy) {
            this.x -= vx;
            this.y -= vy;
        }

        public void subtract(Position pos) {
            x -= pos.x;
            y -= pos.y;
        }

        public static Vector2D subtract(Vector2D v1, Vector2D v2) {
            return new Vector2D(v1.x - v2.x, v1.y - v2.y);
        }

        public static Vector2D subtract(Position v1, Position v2) {
            return new Vector2D(v1.x - v2.x, v1.y - v2.y);
        }

        public Vector2D getSubtracted(Vector2D v) {
            return new Vector2D(this.x - v.x, this.y - v.y);
        }

        public void multiply(double scalar) {
            x *= scalar;
            y *= scalar;
        }

        public Vector2D getMultiplied(double scalar) {
            return new Vector2D(x * scalar, y * scalar);
        }

        public void divide(double scalar) {
            x /= scalar;
            y /= scalar;
        }

        public Vector2D getDivided(double scalar) {
            return new Vector2D(x / scalar, y / scalar);
        }

        public Vector2D getPerp() {
            return new Vector2D(-y, x);
        }

        public double dot(Vector2D v) {
            return (this.x * v.x + this.y * v.y);
        }

        public double dot(double vx, double vy) {
            return (this.x * vx + this.y * vy);
        }

        public static double dot(Vector2D v1, Vector2D v2) {
            return v1.x * v2.x + v1.y * v2.y;
        }

        public double cross(Vector2D v) {
            return (this.x * v.y - this.y * v.x);
        }

        public double cross(double vx, double vy) {
            return (this.x * vy - this.y * vx);
        }

        public static double cross(Vector2D v1, Vector2D v2) {
            return (v1.x * v2.y - v1.y * v2.x);
        }

        public double project(Vector2D v) {
            return (this.dot(v) / this.getLength());
        }

        public double project(double vx, double vy) {
            return (this.dot(vx, vy) / this.getLength());
        }

        public static double project(Vector2D v1, Vector2D v2) {
            return (dot(v1, v2) / v1.getLength());
        }

        public Vector2D getProjectedVector(Vector2D v) {
            return this.getNormalized().getMultiplied(this.dot(v) / this.getLength());
        }

        public Vector2D getProjectedVector(double vx, double vy) {
            return this.getNormalized().getMultiplied(this.dot(vx, vy) / this.getLength());
        }

        public static Vector2D getProjectedVector(Vector2D v1, Vector2D v2) {
            return v1.getNormalized().getMultiplied(Vector2D.dot(v1, v2) / v1.getLength());
        }

        public void rotateBy(double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double rx = x * cos - y * sin;
            y = x * sin + y * cos;
            x = rx;
        }

        public Vector2D getRotatedBy(double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            return new Vector2D(x * cos - y * sin, x * sin + y * cos);
        }

        public void rotateTo(double angle) {
            set(toCartesian(getLength(), angle));
        }

        public Vector2D getRotatedTo(double angle) {
            return toCartesian(getLength(), angle);
        }

        public void reverse() {
            x = -x;
            y = -y;
        }

        public Vector2D getReversed() {
            return new Vector2D(-x, -y);
        }

        @Override
        public Vector2D clone() {
            return new Vector2D(x, y);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof Vector2D) {
                Vector2D v = (Vector2D) obj;
                return (x == v.x) && (y == v.y);
            }
            return false;
        }

        @Override
        public String toString() {
            return "Vector2d[" + x + ", " + y + "]";
        }
    }
}
