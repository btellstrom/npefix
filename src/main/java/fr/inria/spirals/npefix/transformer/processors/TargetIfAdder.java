package fr.inria.spirals.npefix.transformer.processors;

import fr.inria.spirals.npefix.resi.AbnormalExecutionError;
import fr.inria.spirals.npefix.resi.CallChecker;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.code.CtFieldAccessImpl;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.support.reflect.code.CtVariableAccessImpl;
import spoon.support.reflect.reference.CtFieldReferenceImpl;

import java.util.Arrays;

@SuppressWarnings("all")
public class TargetIfAdder extends AbstractProcessor<CtTargetedExpression>{

	private int i=0;
	private int j=0;
	@Override
	public void processingDone() {
		System.out.println("if -->"+i +" (failed:"+j+")");
	}
	

	@Override
	public void process(CtTargetedExpression element) {
		CtExpression target = element.getTarget();
		if(target == null)
			return;
		if(element instanceof CtFieldAccess<?> && ((CtFieldAccess) element).getVariable().isStatic())
			return;
		if(element instanceof CtInvocationImpl<?> && ((CtInvocationImpl) element).getExecutable().isStatic())
			return;
		if(target instanceof CtThisAccess
				|| target instanceof CtSuperAccess
				|| target instanceof CtTypeAccess)
			return;

		CtElement line = element;
		try{
			i++;
			CtElement parent = null;
			boolean found = false;
			boolean needElse = false;
			try{
				while (!found) {
					parent=line.getParent();
					if(parent == null || parent.getParent()==null){
						return;
					}else if(parent.getParent() instanceof CtConstructor && line instanceof CtInvocation 
							&& ((CtInvocation)line).getExecutable().getSimpleName().equals("<init>")){
						return;//premiere ligne constructeur
					}else if(parent instanceof CtReturn || parent instanceof CtThrow){
						line=parent;
						needElse = true;
					}else if(parent instanceof CtBlock){
						found=true;
					}else if(parent instanceof CtStatementList){
						found=true;
					}else if(parent instanceof CtCatch){
						found=true;
					}else if(parent instanceof CtIf){
						line=parent;
						needElse = true;
					}else if(parent instanceof CtAssignment 
							&& ((CtAssignment) parent).getAssigned() instanceof CtFieldAccess){
						return;
					}else if(parent instanceof CtLoop){
						return;
					}else{
						line=parent;
					}
				}
			}catch(ParentNotInitializedException pni){
				System.out.println(line);
				pni.printStackTrace();
				j++;
			}
			if(line instanceof CtLocalVariable && ((CtLocalVariable) line).hasModifier(ModifierKind.FINAL))
				return;
			
			CtExecutableReference execif = getFactory().Core().createExecutableReference();
			execif.setDeclaringType(getFactory().Type().createReference(CallChecker.class));
			execif.setSimpleName("beforeDeref");
			execif.setStatic(true);

			CtInvocationImpl ifInvoc = (CtInvocationImpl) getFactory().Core().createInvocation();
			ifInvoc.setExecutable(execif);
			ifInvoc.setArguments(Arrays.asList(new CtExpression[]{element.getTarget()}));
			
			CtIf encaps = getFactory().Core().createIf();
			encaps.setCondition(ifInvoc);

			CtBlock thenBloc = getFactory().Core().createBlock();

			//add var init
			if(line instanceof CtLocalVariable){
				CtLocalVariable localVar = (CtLocalVariable) line;
				
				CtAssignment assign = getFactory().Core().createAssignment();

				CtVariableAccessImpl va = new CtVariableAccessImpl();
				va.setVariable(localVar.getReference());
				
				assign.setAssigned(va);
				assign.setAssignment(localVar.getDefaultExpression());

				CtLocalVariable previous = getFactory().Core().createLocalVariable();
				previous.setType(localVar.getType());
				previous.setSimpleName(localVar.getSimpleName());
				
				CtTypeReference tmp2 = localVar.getType();
				
				CtExpression arg = null;
				if(tmp2 instanceof CtArrayTypeReference){
					tmp2=((CtArrayTypeReference)tmp2).getDeclaringType();
				}
				if(tmp2==null || tmp2.isAnonymous() || tmp2.getSimpleName()==null || (tmp2.getPackage()==null && tmp2.getSimpleName().length()==1)){
					arg = getFactory().Core().createLiteral();
					arg.setType(getFactory().Type().nullType());
				}else{
					tmp2 = getFactory().Type().createReference(tmp2.getQualifiedName());
					CtFieldReference ctfe = new CtFieldReferenceImpl();
					ctfe.setSimpleName("class");
					ctfe.setDeclaringType(tmp2);
					
					arg = new CtFieldAccessImpl();
					((CtFieldAccessImpl) arg).setVariable(ctfe);
				}
				CtExecutableReference execref = getFactory().Core().createExecutableReference();
				execref.setDeclaringType(getFactory().Type().createReference(CallChecker.class));
				execref.setSimpleName("init");
				execref.setStatic(true);
				
				CtInvocationImpl invoc = (CtInvocationImpl) getFactory().Core().createInvocation();
				invoc.setExecutable(execref);
				invoc.setArguments(Arrays.asList(new CtExpression[]{arg}));
				
				previous.setDefaultExpression(invoc);
				
				((CtLocalVariable) line).insertBefore(previous);
				
				thenBloc.addStatement(assign);
				encaps.setThenStatement(thenBloc);
				((CtLocalVariable) line).replace(encaps);
			}else if(line instanceof CtStatement){
				((CtStatement) line).replace(encaps);
				encaps.setThenStatement(thenBloc);
				thenBloc.addStatement((CtStatement)line);
			}
			
			if(needElse){
				CtConstructorCall npe = getFactory().Core().createConstructorCall();
				npe.setType(getFactory().Type().createReference(AbnormalExecutionError.class));
				
				CtThrow thrower = getFactory().Core().createThrow();
				thrower.setThrownExpression(npe);
				
				encaps.setElseStatement(thrower);
			}
			
		}catch(Throwable t){
			System.out.println(line+"-->"+element);
			t.printStackTrace();
			j++;
		}
	}

}