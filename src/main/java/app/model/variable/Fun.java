package app.model.variable;

import suite.suite.Subject;
import suite.suite.Suite;
import suite.suite.action.Action;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.function.BiPredicate;

public class Fun {

    public static class Const {}

    public static final Const SELF = new Const();

    public static Fun compose(Subject inputs, Subject outputs, Action transition) {
        return new Fun(inputs, outputs, transition);
    }

    public static<T, T1 extends T> Fun assign(Var<T1> source, Var<T> target) {
        return new Fun(Suite.set(source), Suite.set(Var.OWN_VALUE, target), s -> Suite.set(Var.OWN_VALUE, s.direct()));
    }

    public static<T, T1 extends T> Fun suppress(Var<T1> source, Var<T> target, BiPredicate<T1, T> suppressor) {
        return new Fun(Suite.set(Var.OWN_VALUE, source).set(target), Suite.set(Var.OWN_VALUE, target),
                s -> suppressor.test(s.recent().asExpected(), s.asExpected()) ? Suite.set() : s);
    }

    public static<T, T1 extends T> Fun suppressIdentity(Var<T1> source, Var<T> target) {
        return new Fun(Suite.set(Var.OWN_VALUE, source).set(target), Suite.set(Var.OWN_VALUE, target),
                s -> s.direct() == s.recent().direct() ? Suite.set() : s);
    }

    public static<T, T1 extends T> Fun suppressEquality(Var<T1> source, Var<T> target) {
        return new Fun(Suite.set(Var.OWN_VALUE, source).set(target), Suite.set(Var.OWN_VALUE, target),
                s -> Objects.equals(s.direct(), s.recent().direct()) ? Suite.set() : s);
    }

    Subject inputs;
    Subject outputs;
    Action transition;
    boolean detection;

    public Fun(Subject inputs, Subject outputs, Action transition) {
        this.inputs = inputs.front().advance(s -> {
            Var<?> v;
            if(s.assigned(Var.class)) {
                v = s.asExpected();
            } else if(s.direct() == SELF) {
                v = new Var<>(this, false);
            } else {
                v = new Var<>(s.direct(), true);
            }
            v.attachOutput(this);
            return Suite.set(s.key().direct(), v);
        }).toSubject();
        this.outputs = outputs.front().advance(s -> {
            if(s.assigned(Var.class)) {
                Var<?> v = s.asExpected();
                v.attachInput(this);
                return Suite.set(s.key().direct(), new WeakReference<>(v));
            }
            return Suite.set();
        }).toSubject();
        this.transition = transition;
    }

    public void evaluate() {
        Subject inputParams = inputs.front().advance(s -> Suite.set(s.key().direct(), s.asGiven(Var.class).get(this))).toSubject();
        if(detection) {
            detection = false;
            Subject outputParams = transition.play(inputParams);
            outputParams.front().forEach(s -> {
                var key = s.key().direct();
                var output = outputs.get(key);
                if (output.settled()) {
                    WeakReference<Var<?>> ref = output.asExpected();
                    Var<?> v = ref.get();
                    if(v == null) {
                        outputs.unset(key);
                    } else {
                        v.set(s.asExpected(), this);
                    }
                }
            });
        }
    }

    public boolean press(boolean direct) {
        if(detection) return false;
        if(direct)detection = true;
        for(var s : outputs.front()) {
            WeakReference<Var<?>> ref = s.asExpected();
            Var<?> var = ref.get();
            if(var != null && var.press(this)) return true;
        }
        return false;
    }

    public void detach() {
        inputs.front().keys().forEach(this::detachInput);
        inputs = Suite.set();
        outputs = Suite.set();
    }

    public void detachOutput(Object key) {
        outputs.unset(key);
    }

    public void detachOutputVar(Var<?> output) {
        for (var s : outputs.front()){
            WeakReference<Var<?>> ref = s.asExpected();
            Var<?> var = ref.get();
            if(var == null || var.equals(output)) {
                outputs.unset(s.key().direct());
            }
        }
    }

    public void detachInput(Object key) {
        var s = inputs.get(key);
        if(s.settled()) {
            inputs.unset(key);
            Var<?> var = s.asExpected();
            var.detachOutput(this);
        }
    }

    public void detachInputVar(Var<?> input) {
        for (var s : inputs.front()){
            if(s.direct().equals(input)) {
                detachInput(s.key().direct());
            }
        }
    }
}
